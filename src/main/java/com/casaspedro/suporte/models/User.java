package com.casaspedro.suporte.models;

/**
 * Modelo que representa o usuário logado
 * Dados vêm do Active Directory após autenticação
 */
public class User {
    
    private String username;
    private String displayName;
    private String email;
    private String department;
    private String title;  // Cargo: "TI Suporte N1", "Analista", etc.
    
    // Singleton para manter usuário logado na sessão
    private static User currentUser;
    
    public User() {}
    
    public User(String username, String displayName, String title) {
        this.username = username;
        this.displayName = displayName;
        this.title = title;
    }
    
    // Getters e Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    /**
     * Retorna as iniciais do nome (ex: "João Silva" -> "JS")
     */
    public String getInitials() {
        if (displayName == null || displayName.isBlank()) {
            return username != null ? username.substring(0, 1).toUpperCase() : "?";
        }
        
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }
    
    // Métodos estáticos para gerenciar sessão
    public static void setCurrentUser(User user) {
        currentUser = user;
    }
    
    public static User getCurrentUser() {
        return currentUser;
    }
    
    public static boolean isLoggedIn() {
        return currentUser != null;
    }
    
    public static void logout() {
        currentUser = null;
    }
}
