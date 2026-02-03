package com.casaspedro.suporte.controllers;

import com.casaspedro.suporte.services.PlanilhaRepairService;
import com.casaspedro.suporte.services.PlanilhaRepairService.RepairResult;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * ═══════════════════════════════════════════════════════════════
 * PlanilhaController
 * Controller para a tela de Manutenção de Planilhas
 * ═══════════════════════════════════════════════════════════════
 */
public class PlanilhaController implements Initializable {

    // ─────────────────────────────────────────────────────────────
    // FXML BINDINGS
    // ─────────────────────────────────────────────────────────────
    
    @FXML private VBox dropZone;
    @FXML private Label dropZoneLabel;
    @FXML private FontIcon dropZoneIcon;
    @FXML private Label arquivoNomeLabel;
    @FXML private Button btnSelecionar;
    @FXML private Button btnReparar;
    @FXML private Button btnLimpar;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private TextArea logArea;
    @FXML private HBox resultadoBox;
    @FXML private FontIcon resultadoIcon;
    @FXML private Label resultadoTexto;
    
    // ─────────────────────────────────────────────────────────────
    // ESTADO
    // ─────────────────────────────────────────────────────────────
    
    private File arquivoSelecionado;
    private final PlanilhaRepairService repairService;
    private boolean emExecucao = false;
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Construtor
     */
    public PlanilhaController() {
        this.repairService = new PlanilhaRepairService();
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupDragAndDrop();
        setupCallbacks();
        resetUI();
    }
    
    // ─────────────────────────────────────────────────────────────
    // CONFIGURAÇÃO INICIAL
    // ─────────────────────────────────────────────────────────────
    
    /**
     * Configura eventos de Drag & Drop
     */
    private void setupDragAndDrop() {
        // Detecta quando arquivo entra na zona
        dropZone.setOnDragOver(event -> {
            if (event.getGestureSource() != dropZone && hasExcelFile(event.getDragboard())) {
                event.acceptTransferModes(TransferMode.COPY);
                dropZone.getStyleClass().add("drop-zone-active");
            }
            event.consume();
        });
        
        // Remove estilo quando sai
        dropZone.setOnDragExited(event -> {
            dropZone.getStyleClass().remove("drop-zone-active");
            event.consume();
        });
        
        // Processa o arquivo solto
        dropZone.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            
            if (hasExcelFile(db)) {
                for (File file : db.getFiles()) {
                    if (isExcelFile(file)) {
                        selecionarArquivo(file);
                        success = true;
                        break;
                    }
                }
            }
            
            dropZone.getStyleClass().remove("drop-zone-active");
            event.setDropCompleted(success);
            event.consume();
        });
    }
    
    /**
     * Configura callbacks do service
     */
    private void setupCallbacks() {
        repairService.setLogCallback(msg -> Platform.runLater(() -> addLog(msg)));
        repairService.setProgressCallback(prog -> Platform.runLater(() -> updateProgress(prog)));
    }
    
    // ─────────────────────────────────────────────────────────────
    // HANDLERS DE EVENTOS
    // ─────────────────────────────────────────────────────────────
    
    /**
     * Handler: Botão "Selecionar Arquivo"
     */
    @FXML
    private void onSelecionarArquivo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecionar Planilha Excel");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Planilhas Excel", "*.xlsx", "*.xls", "*.xlsm"),
            new FileChooser.ExtensionFilter("Todos os arquivos", "*.*")
        );
        
        // Define diretório inicial (rede ou documentos)
        File initialDir = new File("\\\\10.254.1.236");
        if (!initialDir.exists()) {
            initialDir = new File(System.getProperty("user.home"), "Documents");
        }
        fileChooser.setInitialDirectory(initialDir);
        
        File arquivo = fileChooser.showOpenDialog(dropZone.getScene().getWindow());
        if (arquivo != null) {
            selecionarArquivo(arquivo);
        }
    }
    
    /**
     * Handler: Botão "Reparar Planilha"
     */
    @FXML
    private void onRepararPlanilha() {
        if (arquivoSelecionado == null) {
            showAlert(Alert.AlertType.WARNING, "Aviso", "Nenhum arquivo selecionado", 
                     "Arraste uma planilha ou clique em 'Selecionar Arquivo'");
            return;
        }
        
        if (emExecucao) {
            return;
        }
        
        // Confirmação
        boolean confirmar = showConfirmation(
            "Confirmar Reparo",
            "Reparar: " + arquivoSelecionado.getName(),
            "O Excel será fechado e o cache do Office será limpo.\nDeseja continuar?"
        );
        
        if (!confirmar) {
            return;
        }
        
        executarReparo();
    }
    
    /**
     * Handler: Botão "Limpar"
     */
    @FXML
    private void onLimpar() {
        resetUI();
    }
    
    /**
     * Handler: Botão "Voltar"
     */
    @FXML
    private void onVoltar() {
        // Navegar de volta para a tela principal
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/fxml/main.fxml")
            );
            javafx.scene.Parent root = loader.load();
            dropZone.getScene().setRoot(root);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erro", "Erro ao voltar", e.getMessage());
        }
    }
    
    // ─────────────────────────────────────────────────────────────
    // LÓGICA DE NEGÓCIO
    // ─────────────────────────────────────────────────────────────
    
    /**
     * Seleciona um arquivo para reparo
     */
    private void selecionarArquivo(File arquivo) {
        this.arquivoSelecionado = arquivo;
        
        // Atualiza UI
        arquivoNomeLabel.setText(arquivo.getName());
        arquivoNomeLabel.setVisible(true);
        arquivoNomeLabel.setManaged(true);
        
        dropZoneIcon.setIconLiteral("fas-file-excel");
        dropZoneLabel.setText("Arquivo carregado");
        
        btnReparar.setDisable(false);
        btnLimpar.setDisable(false);
        
        // Esconde resultado anterior
        resultadoBox.setVisible(false);
        resultadoBox.setManaged(false);
        
        addLog("Arquivo selecionado: " + arquivo.getAbsolutePath());
    }
    
    /**
     * Executa o reparo em background
     */
    private void executarReparo() {
        emExecucao = true;
        
        // UI: modo execução
        btnReparar.setDisable(true);
        btnSelecionar.setDisable(true);
        btnLimpar.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressLabel.setVisible(true);
        progressLabel.setManaged(true);
        resultadoBox.setVisible(false);
        resultadoBox.setManaged(false);
        
        logArea.clear();
        addLog("═══════════════════════════════════════");
        addLog("Iniciando reparo de planilha...");
        addLog("═══════════════════════════════════════");
        
        // Task em background
        Task<RepairResult> task = new Task<>() {
            @Override
            protected RepairResult call() {
                return repairService.repararPlanilha(arquivoSelecionado);
            }
        };
        
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            RepairResult resultado = task.getValue();
            finalizarReparo(resultado);
        }));
        
        task.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = e.getSource().getException();
            addLog("ERRO fatal: " + (ex != null ? ex.getMessage() : "Desconhecido"));
            finalizarReparo(new RepairResult(false, "Erro durante execução", 
                java.util.List.of(), java.util.List.of(ex != null ? ex.getMessage() : "Erro")));
        }));
        
        new Thread(task).start();
    }
    
    /**
     * Finaliza o processo de reparo e atualiza UI
     */
    private void finalizarReparo(RepairResult resultado) {
        emExecucao = false;
        
        // UI: modo normal
        btnReparar.setDisable(false);
        btnSelecionar.setDisable(false);
        btnLimpar.setDisable(false);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        progressLabel.setVisible(false);
        progressLabel.setManaged(false);
        
        // Mostrar resultado
        resultadoBox.setVisible(true);
        resultadoBox.setManaged(true);
        
        if (resultado.isSucesso()) {
            resultadoBox.getStyleClass().removeAll("resultado-erro");
            resultadoBox.getStyleClass().add("resultado-sucesso");
            resultadoIcon.setIconLiteral("fas-check-circle");
            resultadoIcon.setStyle("-fx-fill: #10B981;");
            
            if (resultado.temErros()) {
                resultadoTexto.setText("Concluído com avisos");
            } else {
                resultadoTexto.setText("Planilha reparada com sucesso!");
            }
        } else {
            resultadoBox.getStyleClass().removeAll("resultado-sucesso");
            resultadoBox.getStyleClass().add("resultado-erro");
            resultadoIcon.setIconLiteral("fas-times-circle");
            resultadoIcon.setStyle("-fx-fill: #EF4444;");
            resultadoTexto.setText(resultado.getMensagem());
        }
        
        addLog("═══════════════════════════════════════");
        addLog(resultado.isSucesso() ? "Processo finalizado com sucesso" : "Processo finalizado com erros");
    }
    
    // ─────────────────────────────────────────────────────────────
    // UTILITÁRIOS
    // ─────────────────────────────────────────────────────────────
    
    /**
     * Verifica se Dragboard contém arquivo Excel
     */
    private boolean hasExcelFile(Dragboard db) {
        if (db.hasFiles()) {
            for (File file : db.getFiles()) {
                if (isExcelFile(file)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Verifica se arquivo é Excel
     */
    private boolean isExcelFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".xlsm");
    }
    
    /**
     * Adiciona mensagem ao log com timestamp
     */
    private void addLog(String mensagem) {
        String timestamp = LocalTime.now().format(TIME_FORMAT);
        logArea.appendText("[" + timestamp + "] " + mensagem + "\n");
        
        // Auto-scroll
        logArea.setScrollTop(Double.MAX_VALUE);
    }
    
    /**
     * Atualiza barra de progresso
     */
    private void updateProgress(double valor) {
        progressBar.setProgress(valor);
        progressLabel.setText(String.format("%.0f%%", valor * 100));
    }
    
    /**
     * Reseta a UI para estado inicial
     */
    private void resetUI() {
        arquivoSelecionado = null;
        
        dropZoneIcon.setIconLiteral("fas-cloud-download-alt");
        dropZoneLabel.setText("Arraste uma planilha aqui");
        arquivoNomeLabel.setText("");
        arquivoNomeLabel.setVisible(false);
        arquivoNomeLabel.setManaged(false);
        
        btnReparar.setDisable(true);
        btnLimpar.setDisable(true);
        
        progressBar.setProgress(0);
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        progressLabel.setVisible(false);
        progressLabel.setManaged(false);
        
        logArea.clear();
        
        resultadoBox.setVisible(false);
        resultadoBox.setManaged(false);
        resultadoBox.getStyleClass().removeAll("resultado-sucesso", "resultado-erro");
    }
    
    /**
     * Mostra alert
     */
    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.initOwner(dropZone.getScene().getWindow());
        alert.showAndWait();
    }
    
    /**
     * Mostra confirmação
     */
    private boolean showConfirmation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.initOwner(dropZone.getScene().getWindow());
        
        return alert.showAndWait()
                    .filter(r -> r == ButtonType.OK)
                    .isPresent();
    }
}
