package afb.astyann.aiorchestrator.provider;

public interface AIProvider {
    String complete(String prompt, ProviderConfig config);
    String getProviderName();
}
