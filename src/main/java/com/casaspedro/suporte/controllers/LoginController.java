package com.casaspedro.suporte.controllers;

import com.casaspedro.suporte.models.User;
import com.casaspedro.suporte.services.LdapAuthService;
import com.casaspedro.suporte.services.LdapAuthService.AuthResult;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller da tela de Login
 */
public class LoginController implements Initializable {

    @FXML private VBox loginCard;
    @FXML private ImageView logoImage;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button btnLogin;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private HBox loadingBox;
    
    private final LdapAuthService authService = new LdapAuthService();
    private boolean isLoading = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Carregar logo
        try {
            Image logo = new Image(getClass().getResourceAsStream("/images/logo.png"));
            logoImage.setImage(logo);
        } catch (Exception e) {
            System.err.println("Erro ao carregar logo: " + e.getMessage());
        }
        
        // Enter para fazer login
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) passwordField.requestFocus();
        });
        
        passwordField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) onLogin();
        });
        
        // Limpar erro ao digitar
        usernameField.textProperty().addListener((obs, old, newVal) -> hideError());
        passwordField.textProperty().addListener((obs, old, newVal) -> hideError());
        
        // Esconder loading inicialmente
        loadingBox.setVisible(false);
        loadingBox.setManaged(false);
        
        // Foco no campo de usuário
        Platform.runLater(() -> usernameField.requestFocus());
    }

    @FXML
    private void onLogin() {
        if (isLoading) return;
        
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        // Validação básica
        if (username.isEmpty()) {
            showError("Digite seu usuário");
            usernameField.requestFocus();
            return;
        }
        
        if (password.isEmpty()) {
            showError("Digite sua senha");
            passwordField.requestFocus();
            return;
        }
        
        // Iniciar autenticação
        setLoading(true);
        hideError();
        
        Task<AuthResult> task = new Task<>() {
            @Override
            protected AuthResult call() {
                return authService.authenticate(username, password);
            }
        };
        
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            setLoading(false);
            AuthResult result = task.getValue();
            
            if (result.isSuccess()) {
                // Salvar usuário na sessão
                User.setCurrentUser(result.getUser());
                
                // Ir para tela principal
                navigateToMain();
            } else {
                showError(result.getMessage());
                passwordField.clear();
                passwordField.requestFocus();
            }
        }));
        
        task.setOnFailed(e -> Platform.runLater(() -> {
            setLoading(false);
            showError("Erro ao conectar. Tente novamente.");
        }));
        
        new Thread(task).start();
    }

    private void navigateToMain() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();
            loginCard.getScene().setRoot(root);
        } catch (Exception e) {
            showError("Erro ao carregar tela principal");
            e.printStackTrace();
        }
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        btnLogin.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        loadingBox.setVisible(loading);
        loadingBox.setManaged(loading);
        btnLogin.setText(loading ? "Entrando..." : "Entrar");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
