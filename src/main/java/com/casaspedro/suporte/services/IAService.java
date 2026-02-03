package com.casaspedro.suporte.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * ═══════════════════════════════════════════════════════════════
 * IAService - Integração com Ollama (IA Local)
 * 
 * O Ollama roda localmente e processa as perguntas do usuário
 * para sugerir qual ferramenta usar.
 * 
 * REQUISITOS:
 * 1. Instalar Ollama: https://ollama.ai/download
 * 2. Baixar modelo: ollama pull llama3.2
 * 3. Rodar servidor: ollama serve
 * ═══════════════════════════════════════════════════════════════
 */
public class IAService {

    // Configurações do Ollama
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "llama3.2";
    private static final int TIMEOUT_SECONDS = 60;
    
    private final HttpClient httpClient;
    
    // Prompt do sistema - ensina a IA sobre as ferramentas disponíveis
    private static final String SYSTEM_PROMPT = """
        Você é o assistente de TI da Casas Pedro. Seu trabalho é ajudar usuários com problemas de computador.
        
        FERRAMENTAS DISPONÍVEIS:
        1. RECONECTAR_PASTAS - Para problemas com pastas de rede, unidades mapeadas, acesso a servidor, "não encontra o caminho"
        2. LIMPAR_CACHE - Para computador lento, travamentos, erros de memória, liberar espaço
        3. REPARAR_OFFICE - Para problemas com Word, Excel, PowerPoint, Outlook travando, Office não abre
        4. MANUTENCAO_PLANILHA - Para Excel bloqueado, planilha em uso, arquivo corrompido, não consegue salvar planilha
        
        REGRAS:
        - Responda APENAS com o nome da ferramenta mais adequada (ex: RECONECTAR_PASTAS)
        - Se não souber ou não tiver ferramenta adequada, responda: NENHUMA
        - Seja direto, sem explicações
        
        Problema do usuário:
        """;

    public IAService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Analisa o problema do usuário e sugere uma ferramenta
     * 
     * @param problemaUsuario Descrição do problema
     * @return CompletableFuture com a sugestão da IA
     */
    public CompletableFuture<SugestaoIA> analisarProblema(String problemaUsuario) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String resposta = chamarOllama(problemaUsuario);
                return interpretarResposta(resposta, problemaUsuario);
            } catch (Exception e) {
                return new SugestaoIA(
                    Ferramenta.NENHUMA,
                    "Não foi possível conectar à IA. Verifique se o Ollama está rodando.",
                    false
                );
            }
        });
    }

    /**
     * Chat conversacional com a IA
     * Retorna respostas mais naturais e pode sugerir ferramentas
     * 
     * @param mensagem Mensagem do usuário
     * @return CompletableFuture com a resposta do chat
     */
    public CompletableFuture<ChatResponse> chat(String mensagem) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String resposta = chamarOllamaChat(mensagem);
                return interpretarRespostaChat(resposta);
            } catch (Exception e) {
                return new ChatResponse(
                    "Desculpe, não consegui processar sua mensagem. Verifique se o Ollama está rodando.",
                    null,
                    null
                );
            }
        });
    }

    /**
     * Faz chamada HTTP para chat conversacional
     */
    private String chamarOllamaChat(String mensagem) throws Exception {
        String prompt = CHAT_SYSTEM_PROMPT + mensagem;
        
        String jsonBody = """
            {
                "model": "%s",
                "prompt": "%s",
                "stream": false,
                "options": {
                    "temperature": 0.7,
                    "num_predict": 200
                }
            }
            """.formatted(MODEL, escapeJson(prompt));
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama retornou erro: " + response.statusCode());
        }
        
        return extrairResposta(response.body());
    }

    /**
     * Interpreta resposta do chat
     */
    private ChatResponse interpretarRespostaChat(String respostaIA) {
        String respostaUpper = respostaIA.toUpperCase();
        
        Ferramenta tool = null;
        String toolName = null;
        
        // Verifica se a IA sugeriu alguma ferramenta na resposta
        if (respostaUpper.contains("RECONECTAR_PASTAS") || respostaUpper.contains("RECONECTAR PASTAS")) {
            tool = Ferramenta.RECONECTAR_PASTAS;
            toolName = "Reconectar Pastas";
        } else if (respostaUpper.contains("LIMPAR_CACHE") || respostaUpper.contains("LIMPAR CACHE")) {
            tool = Ferramenta.LIMPAR_CACHE;
            toolName = "Limpar Cache";
        } else if (respostaUpper.contains("REPARAR_OFFICE") || respostaUpper.contains("REPARAR OFFICE")) {
            tool = Ferramenta.REPARAR_OFFICE;
            toolName = "Reparar Office";
        } else if (respostaUpper.contains("MANUTENCAO_PLANILHA") || respostaUpper.contains("PLANILHA")) {
            tool = Ferramenta.MANUTENCAO_PLANILHA;
            toolName = "Manutenção de Planilhas";
        }
        
        // Limpa a resposta removendo os nomes técnicos das ferramentas
        String mensagemLimpa = respostaIA
            .replaceAll("(?i)RECONECTAR_PASTAS", "Reconectar Pastas")
            .replaceAll("(?i)LIMPAR_CACHE", "Limpar Cache")
            .replaceAll("(?i)REPARAR_OFFICE", "Reparar Office")
            .replaceAll("(?i)MANUTENCAO_PLANILHA", "Manutenção de Planilhas")
            .trim();
        
        return new ChatResponse(mensagemLimpa, tool, toolName);
    }

    // Prompt para chat conversacional
    private static final String CHAT_SYSTEM_PROMPT = """
        Você é o assistente de TI da Casas Pedro, amigável e prestativo.
        
        FERRAMENTAS DISPONÍVEIS:
        1. RECONECTAR_PASTAS - Problemas com pastas de rede, unidades mapeadas, acesso a servidor
        2. LIMPAR_CACHE - Computador lento, travamentos, erros de memória
        3. REPARAR_OFFICE - Problemas com Word, Excel, PowerPoint, Outlook
        4. MANUTENCAO_PLANILHA - Excel bloqueado, planilha em uso, arquivo corrompido
        
        REGRAS:
        - Seja simpático e use linguagem informal
        - Se identificar um problema que pode ser resolvido com uma ferramenta, mencione-a naturalmente
        - Responda de forma concisa (máximo 2-3 frases)
        - Se não souber algo, seja honesto
        
        Usuário:
        """;

    /**
     * Faz a chamada HTTP para o Ollama
     */
    private String chamarOllama(String problemaUsuario) throws Exception {
        String prompt = SYSTEM_PROMPT + problemaUsuario;
        
        // Monta o JSON da requisição
        String jsonBody = """
            {
                "model": "%s",
                "prompt": "%s",
                "stream": false,
                "options": {
                    "temperature": 0.1,
                    "num_predict": 50
                }
            }
            """.formatted(MODEL, escapeJson(prompt));
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama retornou erro: " + response.statusCode());
        }
        
        // Extrai a resposta do JSON (campo "response")
        return extrairResposta(response.body());
    }

    /**
     * Extrai o campo "response" do JSON retornado pelo Ollama
     */
    private String extrairResposta(String jsonResponse) {
        // Parse simples - procura "response":"..." no JSON
        int start = jsonResponse.indexOf("\"response\":\"");
        if (start == -1) return "";
        
        start += 12; // pula "response":"
        int end = jsonResponse.indexOf("\"", start);
        if (end == -1) return "";
        
        String resposta = jsonResponse.substring(start, end);
        // Decodifica escapes
        resposta = resposta.replace("\\n", "\n").replace("\\\"", "\"");
        return resposta.trim();
    }

    /**
     * Interpreta a resposta da IA e retorna a sugestão estruturada
     */
    private SugestaoIA interpretarResposta(String respostaIA, String problemaOriginal) {
        String respostaUpper = respostaIA.toUpperCase().trim();
        
        // Identifica qual ferramenta foi sugerida
        Ferramenta ferramenta;
        String mensagem;
        
        if (respostaUpper.contains("RECONECTAR_PASTAS") || respostaUpper.contains("RECONECTAR PASTAS")) {
            ferramenta = Ferramenta.RECONECTAR_PASTAS;
            mensagem = "Parece ser um problema de rede. Use 'Reconectar Pastas' para restaurar o acesso.";
            
        } else if (respostaUpper.contains("LIMPAR_CACHE") || respostaUpper.contains("LIMPAR CACHE")) {
            ferramenta = Ferramenta.LIMPAR_CACHE;
            mensagem = "O sistema pode estar com cache cheio. Use 'Limpar Cache' para liberar memória.";
            
        } else if (respostaUpper.contains("REPARAR_OFFICE") || respostaUpper.contains("REPARAR OFFICE")) {
            ferramenta = Ferramenta.REPARAR_OFFICE;
            mensagem = "Problema identificado no Office. Use 'Reparar Office' para corrigir.";
            
        } else if (respostaUpper.contains("MANUTENCAO_PLANILHA") || respostaUpper.contains("PLANILHA")) {
            ferramenta = Ferramenta.MANUTENCAO_PLANILHA;
            mensagem = "Problema com planilha Excel. Use 'Manutenção de Planilhas' para desbloquear.";
            
        } else {
            ferramenta = Ferramenta.NENHUMA;
            mensagem = "Não encontrei uma ferramenta específica para esse problema. Tente descrever de outra forma.";
        }
        
        return new SugestaoIA(ferramenta, mensagem, ferramenta != Ferramenta.NENHUMA);
    }

    /**
     * Escapa caracteres especiais para JSON
     */
    private String escapeJson(String texto) {
        return texto
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Verifica se o Ollama está rodando
     */
    public CompletableFuture<Boolean> verificarConexao() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:11434/api/tags"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // Classes internas
    // ═══════════════════════════════════════════════════════════════

    /**
     * Ferramentas disponíveis no sistema
     */
    public enum Ferramenta {
        RECONECTAR_PASTAS("reconnect-network"),
        LIMPAR_CACHE("clear-cache"),
        REPARAR_OFFICE("repair-office"),
        MANUTENCAO_PLANILHA("planilha"),
        NENHUMA(null);
        
        private final String batName;
        
        Ferramenta(String batName) {
            this.batName = batName;
        }
        
        public String getBatName() {
            return batName;
        }
    }

    /**
     * Resultado da análise da IA
     */
    public static class SugestaoIA {
        private final Ferramenta ferramenta;
        private final String mensagem;
        private final boolean encontrou;
        
        public SugestaoIA(Ferramenta ferramenta, String mensagem, boolean encontrou) {
            this.ferramenta = ferramenta;
            this.mensagem = mensagem;
            this.encontrou = encontrou;
        }
        
        public Ferramenta getFerramenta() { return ferramenta; }
        public String getMensagem() { return mensagem; }
        public boolean encontrouFerramenta() { return encontrou; }
    }

    /**
     * Resposta do chat conversacional
     */
    public static class ChatResponse {
        private final String message;
        private final Ferramenta tool;
        private final String toolName;
        
        public ChatResponse(String message, Ferramenta tool, String toolName) {
            this.message = message;
            this.tool = tool;
            this.toolName = toolName;
        }
        
        public String getMessage() { return message; }
        public Ferramenta getTool() { return tool; }
        public String getToolName() { return toolName; }
        public boolean hasTool() { return tool != null && tool != Ferramenta.NENHUMA; }
    }
}
