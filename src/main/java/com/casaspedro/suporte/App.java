package com.casaspedro.suporte;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Suporte Casas Pedro - App Principal
 * Com tema AtlantaFX PrimerDark
 */
public class App extends Application {

    @Override
    public void init() {
        // Aplica o tema dark moderno ANTES de criar qualquer UI
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(loader.load());
        
        // CSS customizado (complementa o tema, não substitui)
        scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
        
        // Configurar janela
        stage.setTitle("Suporte Casas Pedro");
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        
        // Ícone
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/logo.png")));
        } catch (Exception e) {
            System.err.println("Aviso: Logo não encontrada");
        }
        
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
