package me.yuugao.pullhire.fsp.adapter;

/**
 * The FSP registry seam ({@code docs/fsp_integration.md §1/§7}): resolve the
 * achievement history for an {@code fsp_id}. The MVP ships
 * {@link MockFspRegistryAdapter}; after the hackathon only this implementation is
 * swapped for a real client — the service, its endpoints and the event contracts do
 * not change.
 */
public interface FspRegistryAdapter {

    /**
     * Resolves the registry history for an external FSP participant id.
     *
     * @param fspId external participant id (non-blank; validated by the service)
     * @return the history, or {@link RegistryHistory#error()} on a registry failure
     */
    RegistryHistory resolve(String fspId);
}
