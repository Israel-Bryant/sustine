package com.casaspedro.suporte.controllers;

import com.casaspedro.suporte.services.IAService;
import com.casaspedro.suporte.services.IAService.Ferramenta;
import com.casaspedro.suporte.services.IAService.ChatResponse;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * MainController - Tela Principal com Chat IA e Status do Servidor
 * Suporte Casas Pedro v3.1
 */
public class MainController implements Initializable {

    // ═══════════════════════════════════════════════════════════════
    // FXML BINDINGS
    // ═══════════════════════════════════════════════════════════════
    
    @FXML private BorderPane rootContainer;
    @FXML private HBox searchBox;
    @FXML private TextField searchField;
    @FXML private Button searchBtn;
    
    // Chat
    @FXML private SplitPane mainSplitPane;
    @FXML private VBox chatContainer;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatMessages;
    @FXML private TextField chatInput;
    @FXML private Button chatSendBtn;
    @FXML private Button btnCloseChat;
    @FXML private VBox cardsContainer;
    
    // Cards
    @FXML private VBox cardNetwork;
    @FXML private VBox cardCache;
    @FXML private VBox cardOffice;
    @FXML private VBox cardPlanilha;
    
    // Server Status
    @FXML private StackPane serverStatusContainer;
    @FXML private Circle serverStatusDot;
    @FXML private Circle serverStatusGlow;
    @FXML private Label serverLabel;
    @FXML private Tooltip serverTooltip;

    // ═══════════════════════════════════════════════════════════════
    // ESTADO
    // ═══════════════════════════════════════════════════════════════
    
    private final IAService iaService = new IAService();
    private boolean chatOpen = false;
    private Timeline pulseAnimation;
    private ScheduledExecutorService serverCheckExecutor;
    
    // Constantes
    private static final String BATS_DIR = "bats/";
    private static final String REBOOT_FLAG = "[REBOOT_REQUIRED] true";
    private static final String SERVER_IP = "10.254.1.236";
    private static final int SERVER_CHECK_INTERVAL = 10; // segundos

    // ═══════════════════════════════════════════════════════════════
    // INICIALIZAÇÃO
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSearchField();
        setupServerStatus();
        setupTooltip();
        verificarStatusIA();
        
        // Remove o chat do SplitPane na inicialização
        // Só será adicionado quando o usuário abrir o chat
        mainSplitPane.getItems().remove(chatContainer);
    }

    private void setupSearchField() {
        searchField.setOnAction(e -> onSearch());
    }
    
    private void setupTooltip() {
        Tooltip.install(serverStatusContainer, serverTooltip);
    }

    private void verificarStatusIA() {
        iaService.verificarConexao().thenAccept(conectado -> {
            Platform.runLater(() -> {
                if (conectado) {
                    searchField.setPromptText("Pesquisar com IA... descreva seu problema");
                } else {
                    searchField.setPromptText("IA Offline - descreva seu problema");
                }
            });
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // SERVER STATUS
    // ═══════════════════════════════════════════════════════════════

    private void setupServerStatus() {
        // Status inicial: conectando
        setServerStatus("connecting");
        
        // Inicia animação de pulsar
        startPulseAnimation();
        
        // Verifica conexão imediatamente
        checkServerConnection();
        
        // Inicia verificação periódica
        serverCheckExecutor = Executors.newSingleThreadScheduledExecutor();
        serverCheckExecutor.scheduleAtFixedRate(() -> {
            checkServerConnection();
        }, SERVER_CHECK_INTERVAL, SERVER_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    private void startPulseAnimation() {
        pulseAnimation = new Timeline(
            new KeyFrame(Duration.ZERO, 
                new KeyValue(serverStatusGlow.scaleXProperty(), 1.0),
                new KeyValue(serverStatusGlow.scaleYProperty(), 1.0),
                new KeyValue(serverStatusGlow.opacityProperty(), 0.8)
            ),
            new KeyFrame(Duration.millis(1000), 
                new KeyValue(serverStatusGlow.scaleXProperty(), 1.8),
                new KeyValue(serverStatusGlow.scaleYProperty(), 1.8),
                new KeyValue(serverStatusGlow.opacityProperty(), 0.0)
            )
        );
        pulseAnimation.setCycleCount(Animation.INDEFINITE);
        pulseAnimation.play();
    }

    private void checkServerConnection() {
        CompletableFuture.supplyAsync(() -> {
            try {
                InetAddress address = InetAddress.getByName(SERVER_IP);
                return address.isReachable(3000); // timeout 3s
            } catch (Exception e) {
                return false;
            }
        }).thenAccept(connected -> {
            Platform.runLater(() -> {
                if (connected) {
                    setServerStatus("connected");
                } else {
                    setServerStatus("disconnected");
                }
            });
        });
    }

    private void setServerStatus(String status) {
        serverStatusContainer.getStyleClass().removeAll(
            "status-connected", "status-connecting", "status-disconnected"
        );
        
        switch (status) {
            case "connected" -> {
                serverStatusContainer.getStyleClass().add("status-connected");
                serverTooltip.setText("Conectado");
            }
            case "connecting" -> {
                serverStatusContainer.getStyleClass().add("status-connecting");
                serverTooltip.setText("Conectando...");
            }
            case "disconnected" -> {
                serverStatusContainer.getStyleClass().add("status-disconnected");
                serverTooltip.setText("Desconectado");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CHAT - ABERTURA/FECHAMENTO
    // ═══════════════════════════════════════════════════════════════

    @FXML
    private void onSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;
        
        // Abre o chat se estiver fechado
        if (!chatOpen) {
            openChat();
        }
        
        // Adiciona mensagem do usuário
        addUserMessage(query);
        searchField.clear();
        
        // Processa com IA
        processUserMessage(query);
    }

    private void openChat() {
        chatOpen = true;
        
        // Limpa mensagens anteriores
        chatMessages.getChildren().clear();
        
        // Adiciona o chat de volta ao SplitPane se tiver sido removido
        if (!mainSplitPane.getItems().contains(chatContainer)) {
            mainSplitPane.getItems().add(chatContainer);
        }
        
        // Mostra o chat container
        chatContainer.setVisible(true);
        chatContainer.setManaged(true);
        
        // Foca no input do chat após abrir
        chatInput.requestFocus();
        
        // Mensagem inicial
        addSystemMessage("Como posso ajudar você hoje?");
    }

    @FXML
    private void onCloseChat() {
        // Oculta o painel de chat no SplitPane
        chatContainer.setVisible(false);
        chatContainer.setManaged(false);
        
        // Remove o chat da lista de itens do SplitPane para fechar completamente
        mainSplitPane.getItems().remove(chatContainer);
        
        chatOpen = false;
        chatMessages.getChildren().clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // CHAT - MENSAGENS
    // ═══════════════════════════════════════════════════════════════

    @FXML
    private void onSendMessage() {
        String message = chatInput.getText().trim();
        if (message.isEmpty()) return;
        
        addUserMessage(message);
        chatInput.clear();
        
        processUserMessage(message);
    }

    private void addUserMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_RIGHT);
        container.setPadding(new Insets(0, 0, 0, 80));
        
        VBox bubble = new VBox();
        bubble.getStyleClass().add("chat-message-user");
        
        Label label = new Label(text);
        label.setWrapText(true);
        bubble.getChildren().add(label);
        
        container.getChildren().add(bubble);
        chatMessages.getChildren().add(container);
        
        scrollToBottom();
    }

    private void addAIMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(0, 80, 0, 0));
        
        HBox content = new HBox(10);
        content.setAlignment(Pos.TOP_LEFT);
        
        // Ícone do robô
        FontIcon icon = new FontIcon("fas-robot");
        icon.setIconSize(16);
        icon.setStyle("-fx-fill: #6366F1;");
        
        VBox bubble = new VBox();
        bubble.getStyleClass().add("chat-message-ai");
        
        Label label = new Label(text);
        label.setWrapText(true);
        bubble.getChildren().add(label);
        
        content.getChildren().addAll(icon, bubble);
        container.getChildren().add(content);
        chatMessages.getChildren().add(container);
        
        scrollToBottom();
    }

    private void addSystemMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);
        
        VBox bubble = new VBox();
        bubble.getStyleClass().add("chat-message-system");
        
        Label label = new Label(text);
        label.setWrapText(true);
        bubble.getChildren().add(label);
        
        container.getChildren().add(bubble);
        chatMessages.getChildren().add(container);
        
        scrollToBottom();
    }

    private void addExecutingMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);
        
        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("chat-message-executing");
        
        FontIcon icon = new FontIcon("fas-cog");
        icon.setIconSize(14);
        icon.setStyle("-fx-fill: #22D3EE;");
        
        // Animação de rotação
        RotateTransition rotate = new RotateTransition(Duration.seconds(2), icon);
        rotate.setByAngle(360);
        rotate.setCycleCount(Animation.INDEFINITE);
        rotate.play();
        
        Label label = new Label(text);
        label.setWrapText(true);
        
        content.getChildren().addAll(icon, label);
        container.getChildren().add(content);
        chatMessages.getChildren().add(container);
        
        scrollToBottom();
    }

    private void addSuccessMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);
        
        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("chat-message-success");
        
        FontIcon icon = new FontIcon("fas-check-circle");
        icon.setIconSize(14);
        icon.setStyle("-fx-fill: #10B981;");
        
        Label label = new Label(text);
        label.setWrapText(true);
        
        content.getChildren().addAll(icon, label);
        container.getChildren().add(content);
        chatMessages.getChildren().add(container);
        
        scrollToBottom();
    }

    private void addErrorMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);
        
        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("chat-message-error");
        
        FontIcon icon = new FontIcon("fas-times-circle");
        icon.setIconSize(14);
        icon.setStyle("-fx-fill: #EF4444;");
        
        Label label = new Label(text);
        label.setWrapText(true);
        
        content.getChildren().addAll(icon, label);
        container.getChildren().add(content);
        chatMessages.getChildren().add(container);
        
        scrollToBottom();
    }

    private void addTypingIndicator() {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);
        container.setId("typing-indicator");
        container.setPadding(new Insets(0, 80, 0, 0));
        
        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("chat-typing");
        
        // Ícone do robô
        FontIcon robotIcon = new FontIcon("fas-robot");
        robotIcon.setIconSize(14);
        robotIcon.setStyle("-fx-fill: #6366F1;");
        
        // Container para os pontinhos animados (estilo ChatGPT)
        HBox dotsContainer = new HBox(4);
        dotsContainer.setAlignment(Pos.CENTER);
        dotsContainer.setPrefWidth(25);
        
        // Criar 3 círculos que piscarão
        Circle dot1 = new Circle(3);
        Circle dot2 = new Circle(3);
        Circle dot3 = new Circle(3);
        
        dot1.setFill(javafx.scene.paint.Color.web("#6366F1"));
        dot2.setFill(javafx.scene.paint.Color.web("#6366F1"));
        dot3.setFill(javafx.scene.paint.Color.web("#6366F1"));
        
        dotsContainer.getChildren().addAll(dot1, dot2, dot3);
        
        // Animação de opacidade para cada ponto (delay escalonado)
        Timeline animation = new Timeline();
        animation.setCycleCount(Animation.INDEFINITE);
        
        // Ponto 1: começa visível, depois some
        animation.getKeyFrames().addAll(
            new KeyFrame(Duration.millis(0), new KeyValue(dot1.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(300), new KeyValue(dot1.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(600), new KeyValue(dot1.opacityProperty(), 0.4)),
            new KeyFrame(Duration.millis(900), new KeyValue(dot1.opacityProperty(), 0.4)),
            new KeyFrame(Duration.millis(1200), new KeyValue(dot1.opacityProperty(), 1.0))
        );
        
        // Ponto 2: delay de 200ms
        animation.getKeyFrames().addAll(
            new KeyFrame(Duration.millis(200), new KeyValue(dot2.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(500), new KeyValue(dot2.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(800), new KeyValue(dot2.opacityProperty(), 0.4)),
            new KeyFrame(Duration.millis(1100), new KeyValue(dot2.opacityProperty(), 0.4))
        );
        
        // Ponto 3: delay de 400ms
        animation.getKeyFrames().addAll(
            new KeyFrame(Duration.millis(400), new KeyValue(dot3.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(700), new KeyValue(dot3.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(1000), new KeyValue(dot3.opacityProperty(), 0.4)),
            new KeyFrame(Duration.millis(1300), new KeyValue(dot3.opacityProperty(), 0.4))
        );
        
        animation.play();
        
        content.getChildren().addAll(robotIcon, dotsContainer);
        container.getChildren().add(content);
        chatMessages.getChildren().add(container);
        
        scrollToBottom();
    }

    private void removeTypingIndicator() {
        chatMessages.getChildren().removeIf(node -> 
            "typing-indicator".equals(node.getId())
        );
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    // ═══════════════════════════════════════════════════════════════
    // CHAT - PROCESSAMENTO DE MENSAGENS
    // ═══════════════════════════════════════════════════════════════

    private void processUserMessage(String message) {
        // Verifica se é um comando direto
        String msgLower = message.toLowerCase();
        
        // Comandos diretos para executar ferramentas
        if (isDirectCommand(msgLower, "reconectar", "reconecta", "pastas", "rede", "unidade")) {
            executeToolFromChat("reconnect-network", "Reconectar Pastas");
            return;
        }
        
        if (isDirectCommand(msgLower, "limpar cache", "limpa cache", "limpar o cache", "limpa o cache")) {
            executeToolFromChat("clear-cache", "Limpar Cache");
            return;
        }
        
        if (isDirectCommand(msgLower, "reparar office", "repara office", "consertar office", "conserta office")) {
            executeToolFromChat("repair-office", "Reparar Office");
            return;
        }
        
        if (isDirectCommand(msgLower, "planilha", "excel bloqueado", "desbloquear planilha", "desbloquear excel")) {
            addAIMessage("Para manutenção de planilhas, vou abrir a ferramenta especializada.");
            Platform.runLater(() -> {
                try { Thread.sleep(1000); } catch (Exception ignored) {}
                onAbrirPlanilha();
            });
            return;
        }
        
        // Se não for comando direto, consulta a IA
        addTypingIndicator();
        
        chatInput.setDisable(true);
        chatSendBtn.setDisable(true);
        
        iaService.chat(message).thenAccept(response -> {
            Platform.runLater(() -> {
                removeTypingIndicator();
                chatInput.setDisable(false);
                chatSendBtn.setDisable(false);
                chatInput.requestFocus();
                
                // Verifica se a IA sugeriu uma ferramenta
                if (response.hasTool()) {
                    addAIMessage(response.getMessage());
                    
                    // Adiciona botões de ação
                    addToolSuggestion(response.getTool(), response.getToolName());
                } else {
                    addAIMessage(response.getMessage());
                }
            });
        });
    }

    private boolean isDirectCommand(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void addToolSuggestion(Ferramenta tool, String toolName) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);
        container.setSpacing(10);
        container.setPadding(new Insets(8, 0, 8, 0));
        
        Button btnExecute = new Button("Executar " + toolName);
        btnExecute.getStyleClass().add("chat-action-btn");
        btnExecute.setStyle("-fx-background-color: #6366F1; -fx-text-fill: white; " +
                          "-fx-background-radius: 8; -fx-padding: 8 16; -fx-cursor: hand;");
        
        btnExecute.setOnAction(e -> {
            container.setVisible(false);
            container.setManaged(false);
            executeToolFromChat(tool.getBatName(), toolName);
        });
        
        Button btnCancel = new Button("Não, obrigado");
        btnCancel.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748B; " +
                          "-fx-border-color: #64748B; -fx-border-radius: 8; " +
                          "-fx-background-radius: 8; -fx-padding: 8 16; -fx-cursor: hand;");
        
        btnCancel.setOnAction(e -> {
            container.setVisible(false);
            container.setManaged(false);
            addSystemMessage("Ok! Se precisar de mais alguma coisa, é só perguntar.");
        });
        
        container.getChildren().addAll(btnExecute, btnCancel);
        chatMessages.getChildren().add(container);
        
        scrollToBottom();
    }

    private void executeToolFromChat(String batName, String toolName) {
        if ("planilha".equals(batName)) {
            addAIMessage("Abrindo Manutenção de Planilhas...");
            Platform.runLater(this::onAbrirPlanilha);
            return;
        }
        
        addExecutingMessage("Executando " + toolName + "...");
        
        String batPath = BATS_DIR + batName + ".bat";
        File batFile = new File(batPath);
        
        if (!batFile.exists()) {
            addErrorMessage("Arquivo não encontrado: " + batPath);
            return;
        }
        
        Task<BatResult> task = new Task<>() {
            @Override
            protected BatResult call() throws Exception {
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", batFile.getAbsolutePath());
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                StringBuilder output = new StringBuilder();
                boolean rebootRequired = false;
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        if (line.contains(REBOOT_FLAG)) {
                            rebootRequired = true;
                        }
                    }
                }
                
                int exitCode = process.waitFor();
                return new BatResult(exitCode, output.toString(), rebootRequired);
            }
        };
        
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            BatResult result = task.getValue();
            
            if (result.exitCode == 0) {
                if (result.rebootRequired) {
                    addSuccessMessage(toolName + " executado com sucesso!");
                    addSystemMessage("É necessário reiniciar o computador para aplicar as alterações.");
                    addRebootButtons();
                } else {
                    addSuccessMessage(toolName + " executado com sucesso!");
                }
            } else {
                addErrorMessage("Falha ao executar. Código: " + result.exitCode);
            }
        }));
        
        task.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = e.getSource().getException();
            addErrorMessage("Erro: " + (ex != null ? ex.getMessage() : "Desconhecido"));
        }));
        
        new Thread(task).start();
    }

    private void addRebootButtons() {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);
        container.setSpacing(10);
        container.setPadding(new Insets(8, 0, 8, 0));
        
        Button btnReboot = new Button("Reiniciar Agora");
        btnReboot.setStyle("-fx-background-color: #F59E0B; -fx-text-fill: white; " +
                          "-fx-background-radius: 8; -fx-padding: 8 16; -fx-cursor: hand;");
        
        btnReboot.setOnAction(e -> {
            container.setVisible(false);
            container.setManaged(false);
            executeReboot();
        });
        
        Button btnLater = new Button("Depois");
        btnLater.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748B; " +
                          "-fx-border-color: #64748B; -fx-border-radius: 8; " +
                          "-fx-background-radius: 8; -fx-padding: 8 16; -fx-cursor: hand;");
        
        btnLater.setOnAction(e -> {
            container.setVisible(false);
            container.setManaged(false);
            addSystemMessage("Lembre-se de reiniciar quando possível.");
        });
        
        container.getChildren().addAll(btnReboot, btnLater);
        chatMessages.getChildren().add(container);
        
        scrollToBottom();
    }

    // ═══════════════════════════════════════════════════════════════
    // HANDLERS DOS CARDS
    // ═══════════════════════════════════════════════════════════════

    @FXML
    private void onCardClick(MouseEvent event) {
        VBox card = (VBox) event.getSource();
        String batName = (String) card.getUserData();
        
        if (batName == null || batName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Aviso", "Ferramenta não configurada", null);
            return;
        }
        
        String toolName = getToolDisplayName(batName);
        
        String confirmMessage = "Deseja executar esta ferramenta agora?";
        if ("clear-cache".equals(batName)) {
            confirmMessage = "Esta operação requer reinicialização do computador.\n\nDeseja continuar?";
        }
        
        boolean confirmed = showConfirmation(
            "Confirmar Execução",
            "Executar: " + toolName,
            confirmMessage
        );
        
        if (confirmed) {
            executeBatWithRebootDetection(card, batName);
        }
    }

    @FXML
    private void onAbrirPlanilha() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/planilha.fxml"));
            Parent root = loader.load();
            rootContainer.getScene().setRoot(root);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao abrir Manutenção de Planilhas", e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeBatWithRebootDetection(VBox card, String batName) {
        String batPath = BATS_DIR + batName + ".bat";
        File batFile = new File(batPath);
        
        if (!batFile.exists()) {
            showAlert(Alert.AlertType.ERROR, "Erro", "Arquivo não encontrado", 
                      "O script \"" + batPath + "\" não foi localizado.");
            return;
        }
        
        card.getStyleClass().add("running");
        card.setDisable(true);
        
        Task<BatResult> task = new Task<>() {
            @Override
            protected BatResult call() throws Exception {
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", batFile.getAbsolutePath());
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                StringBuilder output = new StringBuilder();
                boolean rebootRequired = false;
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        if (line.contains(REBOOT_FLAG)) {
                            rebootRequired = true;
                        }
                    }
                }
                
                int exitCode = process.waitFor();
                return new BatResult(exitCode, output.toString(), rebootRequired);
            }
        };
        
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            card.getStyleClass().remove("running");
            card.setDisable(false);
            
            BatResult result = task.getValue();
            
            if (result.exitCode == 0) {
                card.getStyleClass().add("success");
                
                if (result.rebootRequired) {
                    handleRebootRequired(getToolDisplayName(batName));
                } else {
                    showAlert(Alert.AlertType.INFORMATION, "Sucesso", "Execução concluída", 
                              getToolDisplayName(batName) + " executado com sucesso!");
                }
            } else {
                card.getStyleClass().add("error");
                showAlert(Alert.AlertType.ERROR, "Erro", "Falha na execução", 
                          "Código de saída: " + result.exitCode);
            }
            
            clearCardStateAfterDelay(card, 3000);
        }));
        
        task.setOnFailed(e -> Platform.runLater(() -> {
            card.getStyleClass().remove("running");
            card.setDisable(false);
            card.getStyleClass().add("error");
            
            Throwable ex = e.getSource().getException();
            showAlert(Alert.AlertType.ERROR, "Erro", "Falha ao executar", 
                      ex != null ? ex.getMessage() : "Erro desconhecido");
            
            clearCardStateAfterDelay(card, 3000);
        }));
        
        new Thread(task).start();
    }

    private void handleRebootRequired(String toolName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reinicialização Necessária");
        alert.setHeaderText(toolName + " executado com sucesso!");
        alert.setContentText("Para aplicar as alterações, é necessário reiniciar.\n\nDeseja reiniciar agora?");
        alert.initOwner(rootContainer.getScene().getWindow());
        
        ButtonType btnReiniciar = new ButtonType("Reiniciar Agora", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnDepois = new ButtonType("Depois", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnReiniciar, btnDepois);
        
        Optional<ButtonType> result = alert.showAndWait();
        
        if (result.isPresent() && result.get() == btnReiniciar) {
            executeReboot();
        }
    }

    private void executeReboot() {
        try {
            addSystemMessage("Reiniciando em 5 segundos...");
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Runtime.getRuntime().exec("shutdown /r /t 5");
                } catch (Exception e) {
                    Platform.runLater(() -> addErrorMessage("Falha ao reiniciar: " + e.getMessage()));
                }
            }).start();
        } catch (Exception e) {
            addErrorMessage("Falha ao reiniciar: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITÁRIOS
    // ═══════════════════════════════════════════════════════════════

    private void clearCardStateAfterDelay(VBox card, int delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                Platform.runLater(() -> card.getStyleClass().removeAll("success", "error"));
            } catch (InterruptedException ignored) {}
        }).start();
    }

    private String getToolDisplayName(String batName) {
        if (batName == null) return "Ferramenta";
        return switch (batName) {
            case "reconnect-network" -> "Reconectar Pastas";
            case "clear-cache" -> "Limpar Cache";
            case "repair-office" -> "Reparar Office";
            case "planilha" -> "Manutenção de Planilhas";
            default -> batName;
        };
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.initOwner(rootContainer.getScene().getWindow());
        alert.showAndWait();
    }

    private boolean showConfirmation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.initOwner(rootContainer.getScene().getWindow());
        return alert.showAndWait().filter(r -> r == ButtonType.OK).isPresent();
    }

    // Cleanup ao fechar
    public void shutdown() {
        if (serverCheckExecutor != null) {
            serverCheckExecutor.shutdown();
        }
        if (pulseAnimation != null) {
            pulseAnimation.stop();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CLASSES INTERNAS
    // ═══════════════════════════════════════════════════════════════

    private static class BatResult {
        final int exitCode;
        final String output;
        final boolean rebootRequired;
        
        BatResult(int exitCode, String output, boolean rebootRequired) {
            this.exitCode = exitCode;
            this.output = output;
            this.rebootRequired = rebootRequired;
        }
    }
}
