package com.casaspedro.suporte.services;

import com.casaspedro.suporte.models.User;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import java.util.Hashtable;

/**
 * Serviço de autenticação via Active Directory (LDAP)
 * 
 * CONFIGURAÇÃO NECESSÁRIA:
 * - LDAP_SERVER: endereço do servidor AD
 * - LDAP_BASE_DN: base DN para busca de usuários
 * - LDAP_DOMAIN: domínio para autenticação
 */
public class LdapAuthService {
    
    // ═══════════════════════════════════════════════════════════════
    // CONFIGURAÇÕES DO AD - ALTERE AQUI
    // ═══════════════════════════════════════════════════════════════
    
    private static final String LDAP_SERVER = "ldap://10.254.1.236:389";  // Servidor AD
    private static final String LDAP_BASE_DN = "DC=casaspedro,DC=local";  // Base DN
    private static final String LDAP_DOMAIN = "casaspedro.local";          // Domínio
    
    // Modo de desenvolvimento (true = aceita qualquer login)
    private static final boolean DEV_MODE = true;
    
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Autentica usuário no Active Directory
     * 
     * @param username Nome de usuário (sem domínio)
     * @param password Senha
     * @return User com dados do AD ou null se falhar
     */
    public AuthResult authenticate(String username, String password) {
        
        // Validação básica
        if (username == null || username.isBlank()) {
            return new AuthResult(false, "Usuário não informado", null);
        }
        if (password == null || password.isBlank()) {
            return new AuthResult(false, "Senha não informada", null);
        }
        
        // Modo desenvolvimento - aceita qualquer login
        if (DEV_MODE) {
            return authenticateDev(username, password);
        }
        
        // Autenticação real via LDAP
        return authenticateLdap(username, password);
    }
    
    /**
     * Autenticação em modo desenvolvimento (mock)
     */
    private AuthResult authenticateDev(String username, String password) {
        // Simula delay de rede
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        
        // Aceita qualquer senha em modo dev
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(formatDisplayName(username));
        user.setTitle("TI • Suporte N1");  // Cargo padrão
        user.setEmail(username + "@casaspedro.com.br");
        user.setDepartment("Tecnologia da Informação");
        
        return new AuthResult(true, "Login realizado com sucesso", user);
    }
    
    /**
     * Autenticação real via LDAP/AD
     * DESCOMENTE E CONFIGURE QUANDO FOR USAR EM PRODUÇÃO
     */
    private AuthResult authenticateLdap(String username, String password) {
        DirContext ctx = null;
        
        try {
            // Configuração da conexão LDAP
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, LDAP_SERVER);
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, username + "@" + LDAP_DOMAIN);
            env.put(Context.SECURITY_CREDENTIALS, password);
            
            // Timeout de conexão
            env.put("com.sun.jndi.ldap.connect.timeout", "5000");
            env.put("com.sun.jndi.ldap.read.timeout", "5000");
            
            // Tenta conectar (isso valida as credenciais)
            ctx = new InitialDirContext(env);
            
            // Busca dados do usuário
            User user = fetchUserData(ctx, username);
            
            if (user != null) {
                return new AuthResult(true, "Login realizado com sucesso", user);
            } else {
                return new AuthResult(true, "Login OK, mas não foi possível carregar dados", 
                    new User(username, username, "Usuário"));
            }
            
        } catch (javax.naming.AuthenticationException e) {
            return new AuthResult(false, "Usuário ou senha incorretos", null);
            
        } catch (javax.naming.CommunicationException e) {
            return new AuthResult(false, "Não foi possível conectar ao servidor", null);
            
        } catch (Exception e) {
            return new AuthResult(false, "Erro de autenticação: " + e.getMessage(), null);
            
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * Busca dados adicionais do usuário no AD
     */
    private User fetchUserData(DirContext ctx, String username) {
        try {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{
                "displayName", "mail", "title", "department", "sAMAccountName"
            });
            
            String filter = "(sAMAccountName=" + username + ")";
            NamingEnumeration<SearchResult> results = ctx.search(LDAP_BASE_DN, filter, controls);
            
            if (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();
                
                User user = new User();
                user.setUsername(getAttr(attrs, "sAMAccountName", username));
                user.setDisplayName(getAttr(attrs, "displayName", username));
                user.setEmail(getAttr(attrs, "mail", ""));
                user.setTitle(getAttr(attrs, "title", "Colaborador"));
                user.setDepartment(getAttr(attrs, "department", ""));
                
                return user;
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao buscar dados do usuário: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Helper para extrair atributo com valor padrão
     */
    private String getAttr(Attributes attrs, String name, String defaultValue) {
        try {
            Attribute attr = attrs.get(name);
            if (attr != null && attr.get() != null) {
                return attr.get().toString();
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }
    
    /**
     * Formata nome de exibição a partir do username
     */
    private String formatDisplayName(String username) {
        if (username == null) return "Usuário";
        
        // Remove números e caracteres especiais
        String name = username.replaceAll("[^a-zA-Z.]", " ");
        
        // Capitaliza cada palavra
        StringBuilder result = new StringBuilder();
        for (String part : name.split("[.\\s]+")) {
            if (!part.isEmpty()) {
                if (result.length() > 0) result.append(" ");
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        
        return result.length() > 0 ? result.toString() : username;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Classe de resultado
    // ═══════════════════════════════════════════════════════════════
    
    public static class AuthResult {
        private final boolean success;
        private final String message;
        private final User user;
        
        public AuthResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public User getUser() { return user; }
    }
}
