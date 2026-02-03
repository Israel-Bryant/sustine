package com.casaspedro.suporte.services;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ═══════════════════════════════════════════════════════════════
 * PlanilhaRepairService
 * Serviço responsável por reparar planilhas Excel com problemas
 * de bloqueio, cache e conectividade.
 * ═══════════════════════════════════════════════════════════════
 */
public class PlanilhaRepairService {

    // Servidor de rede principal
    private static final String SERVIDOR_REDE = "10.254.1.236";
    private static final int TIMEOUT_CONEXAO_MS = 3000;
    
    // Caminhos do Office
    private static final String OFFICE_CACHE_PATH = 
        System.getenv("LOCALAPPDATA") + "\\Microsoft\\Office\\16.0\\OfficeFileCache";
    
    // Callback para logs
    private Consumer<String> logCallback;
    private Consumer<Double> progressCallback;
    
    /**
     * Construtor padrão
     */
    public PlanilhaRepairService() {
        this.logCallback = msg -> {}; // no-op default
        this.progressCallback = prog -> {};
    }
    
    /**
     * Define callback para receber mensagens de log
     */
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }
    
    /**
     * Define callback para atualizar progresso (0.0 a 1.0)
     */
    public void setProgressCallback(Consumer<Double> callback) {
        this.progressCallback = callback;
    }
    
    /**
     * Executa o reparo completo da planilha
     * @param arquivoPlanilha Arquivo .xlsx ou .xls
     * @return RepairResult com status e detalhes
     */
    public RepairResult repararPlanilha(File arquivoPlanilha) {
        List<String> etapasExecutadas = new ArrayList<>();
        List<String> erros = new ArrayList<>();
        
        try {
            // Validação inicial
            log("🔍 Validando arquivo...");
            progress(0.05);
            
            if (!validarArquivo(arquivoPlanilha)) {
                return new RepairResult(false, "Arquivo inválido ou não é uma planilha Excel", etapasExecutadas, erros);
            }
            etapasExecutadas.add("Arquivo validado: " + arquivoPlanilha.getName());
            
            // Etapa 1: Fechar Excel
            log("📌 Etapa 1/5: Fechando instâncias do Excel...");
            progress(0.15);
            ResultadoEtapa r1 = fecharExcel();
            etapasExecutadas.add(r1.mensagem);
            if (!r1.sucesso) erros.add(r1.mensagem);
            
            // Etapa 2: Remover arquivo de lock
            log("🔓 Etapa 2/5: Removendo arquivo de bloqueio (~$)...");
            progress(0.35);
            ResultadoEtapa r2 = removerArquivoLock(arquivoPlanilha);
            etapasExecutadas.add(r2.mensagem);
            if (!r2.sucesso && r2.critico) erros.add(r2.mensagem);
            
            // Etapa 3: Remover Zone.Identifier
            log("🛡️ Etapa 3/5: Removendo bloqueio de origem do Windows...");
            progress(0.50);
            ResultadoEtapa r3 = removerZoneIdentifier(arquivoPlanilha);
            etapasExecutadas.add(r3.mensagem);
            if (!r3.sucesso && r3.critico) erros.add(r3.mensagem);
            
            // Etapa 4: Limpar cache do Office
            log("🧹 Etapa 4/5: Limpando cache do Office...");
            progress(0.70);
            ResultadoEtapa r4 = limparCacheOffice();
            etapasExecutadas.add(r4.mensagem);
            if (!r4.sucesso && r4.critico) erros.add(r4.mensagem);
            
            // Etapa 5: Testar conectividade
            log("🌐 Etapa 5/5: Testando conectividade com servidor...");
            progress(0.90);
            ResultadoEtapa r5 = testarConectividadeServidor();
            etapasExecutadas.add(r5.mensagem);
            if (!r5.sucesso) erros.add(r5.mensagem);
            
            // Resultado final
            progress(1.0);
            
            if (erros.isEmpty()) {
                log("✅ Reparo concluído com sucesso!");
                return new RepairResult(true, "Planilha reparada com sucesso", etapasExecutadas, erros);
            } else {
                log("⚠️ Reparo concluído com avisos");
                return new RepairResult(true, "Reparo concluído com alguns avisos", etapasExecutadas, erros);
            }
            
        } catch (Exception e) {
            log("❌ Erro durante o reparo: " + e.getMessage());
            erros.add("Erro inesperado: " + e.getMessage());
            return new RepairResult(false, "Falha no reparo", etapasExecutadas, erros);
        }
    }
    
    /**
     * Valida se o arquivo é uma planilha Excel válida
     */
    private boolean validarArquivo(File arquivo) {
        if (arquivo == null || !arquivo.exists()) {
            return false;
        }
        
        String nome = arquivo.getName().toLowerCase();
        return nome.endsWith(".xlsx") || nome.endsWith(".xls") || nome.endsWith(".xlsm");
    }
    
    /**
     * Etapa 1: Fecha todas as instâncias do Excel
     */
    private ResultadoEtapa fecharExcel() {
        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/f", "/im", "excel.exe");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return new ResultadoEtapa(true, "Excel fechado com sucesso", false);
            } else {
                // Exit code 128 = processo não encontrado (Excel não estava aberto)
                return new ResultadoEtapa(true, "Nenhuma instância do Excel em execução", false);
            }
        } catch (Exception e) {
            return new ResultadoEtapa(false, "Não foi possível fechar o Excel: " + e.getMessage(), false);
        }
    }
    
    /**
     * Etapa 2: Remove o arquivo de lock (~$NomeArquivo.xlsx)
     */
    private ResultadoEtapa removerArquivoLock(File arquivoPlanilha) {
        try {
            String nomeOriginal = arquivoPlanilha.getName();
            String nomeLock = "~$" + nomeOriginal;
            File arquivoLock = new File(arquivoPlanilha.getParentFile(), nomeLock);
            
            if (arquivoLock.exists()) {
                if (arquivoLock.delete()) {
                    return new ResultadoEtapa(true, "Arquivo de bloqueio removido: " + nomeLock, false);
                } else {
                    return new ResultadoEtapa(false, "Não foi possível remover: " + nomeLock, true);
                }
            } else {
                return new ResultadoEtapa(true, "Nenhum arquivo de bloqueio encontrado", false);
            }
        } catch (Exception e) {
            return new ResultadoEtapa(false, "Erro ao remover lock: " + e.getMessage(), false);
        }
    }
    
    /**
     * Etapa 3: Remove o Zone.Identifier (marca de arquivo baixado da internet)
     */
    private ResultadoEtapa removerZoneIdentifier(File arquivoPlanilha) {
        try {
            Path zoneIdentifier = Paths.get(arquivoPlanilha.getAbsolutePath() + ":Zone.Identifier");
            
            // Verifica se existe usando comando dir (ADS não é visível normalmente)
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", 
                "dir /r \"" + arquivoPlanilha.getAbsolutePath() + "\" | findstr Zone.Identifier");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                // Existe Zone.Identifier, tentar remover
                Files.deleteIfExists(zoneIdentifier);
                return new ResultadoEtapa(true, "Bloqueio de origem removido", false);
            } else {
                return new ResultadoEtapa(true, "Arquivo não possui bloqueio de origem", false);
            }
        } catch (Exception e) {
            // Não é crítico se falhar
            return new ResultadoEtapa(true, "Verificação de origem concluída", false);
        }
    }
    
    /**
     * Etapa 4: Limpa o cache do Office
     */
    private ResultadoEtapa limparCacheOffice() {
        try {
            File cacheDir = new File(OFFICE_CACHE_PATH);
            
            if (!cacheDir.exists()) {
                return new ResultadoEtapa(true, "Pasta de cache não encontrada (OK)", false);
            }
            
            int arquivosRemovidos = 0;
            File[] arquivos = cacheDir.listFiles();
            
            if (arquivos != null) {
                for (File arquivo : arquivos) {
                    try {
                        if (arquivo.isFile() && arquivo.delete()) {
                            arquivosRemovidos++;
                        }
                    } catch (Exception ignored) {
                        // Alguns arquivos podem estar em uso
                    }
                }
            }
            
            if (arquivosRemovidos > 0) {
                return new ResultadoEtapa(true, "Cache limpo: " + arquivosRemovidos + " arquivo(s) removido(s)", false);
            } else {
                return new ResultadoEtapa(true, "Cache já estava limpo", false);
            }
            
        } catch (Exception e) {
            return new ResultadoEtapa(false, "Erro ao limpar cache: " + e.getMessage(), false);
        }
    }
    
    /**
     * Etapa 5: Testa conectividade com o servidor de rede
     */
    private ResultadoEtapa testarConectividadeServidor() {
        try {
            InetAddress servidor = InetAddress.getByName(SERVIDOR_REDE);
            
            if (servidor.isReachable(TIMEOUT_CONEXAO_MS)) {
                return new ResultadoEtapa(true, "Servidor " + SERVIDOR_REDE + " acessível", false);
            } else {
                return new ResultadoEtapa(false, "Servidor " + SERVIDOR_REDE + " não respondeu", true);
            }
        } catch (Exception e) {
            return new ResultadoEtapa(false, "Não foi possível conectar ao servidor: " + e.getMessage(), true);
        }
    }
    
    /**
     * Envia mensagem para o callback de log
     */
    private void log(String mensagem) {
        if (logCallback != null) {
            logCallback.accept(mensagem);
        }
    }
    
    /**
     * Atualiza progresso
     */
    private void progress(double valor) {
        if (progressCallback != null) {
            progressCallback.accept(valor);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // Classes internas para resultados
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Resultado de uma etapa individual
     */
    private static class ResultadoEtapa {
        boolean sucesso;
        String mensagem;
        boolean critico;
        
        ResultadoEtapa(boolean sucesso, String mensagem, boolean critico) {
            this.sucesso = sucesso;
            this.mensagem = mensagem;
            this.critico = critico;
        }
    }
    
    /**
     * Resultado final do reparo
     */
    public static class RepairResult {
        private final boolean sucesso;
        private final String mensagem;
        private final List<String> etapas;
        private final List<String> erros;
        
        public RepairResult(boolean sucesso, String mensagem, List<String> etapas, List<String> erros) {
            this.sucesso = sucesso;
            this.mensagem = mensagem;
            this.etapas = etapas;
            this.erros = erros;
        }
        
        public boolean isSucesso() { return sucesso; }
        public String getMensagem() { return mensagem; }
        public List<String> getEtapas() { return etapas; }
        public List<String> getErros() { return erros; }
        
        public boolean temErros() { return !erros.isEmpty(); }
    }
}
