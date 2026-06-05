package org.eclipse.mosaic.app.maga.livestate;

final class LiveStaticInfrastructureCatalog {

    private final int gatewayCount;
    private final int edgeNodeCount;
    private final int cloudNodeCount;

    private LiveStaticInfrastructureCatalog(int gatewayCount, int edgeNodeCount, int cloudNodeCount) {
        this.gatewayCount = gatewayCount;
        this.edgeNodeCount = edgeNodeCount;
        this.cloudNodeCount = cloudNodeCount;
    }

    static LiveStaticInfrastructureCatalog fromConfig(MaGaLiveStateConfig config) {
        MaGaLiveStateConfig.StaticInfrastructure infrastructure = config.getStaticInfrastructure();
        return new LiveStaticInfrastructureCatalog(
                infrastructure.gateways.size(),
                infrastructure.edgeNodes.size(),
                infrastructure.cloudNodes.size()
        );
    }

    int getGatewayCount() {
        return gatewayCount;
    }

    int getEdgeNodeCount() {
        return edgeNodeCount;
    }

    int getCloudNodeCount() {
        return cloudNodeCount;
    }
}
