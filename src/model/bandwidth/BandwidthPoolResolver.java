package model.bandwidth;

import model.node.NodeCandidate;
import model.node.NodeType;
import model.snapshot.AccessGatewaySnapshot;
import model.snapshot.AccessLinkSnapshot;
import model.snapshot.BandwidthPoolSnapshot;
import model.snapshot.SystemSnapshot;

import java.util.Objects;

/** Risolve il pool radio consumato da una scelta remota. */
public final class BandwidthPoolResolver {
    public BandwidthPoolSnapshot resolve(SystemSnapshot snapshot, NodeCandidate candidate) {
        Objects.requireNonNull(snapshot,"snapshot must not be null."); Objects.requireNonNull(candidate,"candidate must not be null.");
        if(candidate.getType()==NodeType.LOCAL) throw new IllegalArgumentException("LOCAL candidates do not consume remote bandwidth pools.");

        // V2V diretto o override esplicito.
        if(candidate.getBandwidthPoolId()!=null) return requirePool(snapshot,candidate.getBandwidthPoolId());

        // EDGE e CLOUD condividono il pool del gateway radio attivo del veicolo sorgente.
        if(candidate.getType()==NodeType.EDGE || candidate.getType()==NodeType.CLOUD) {
            AccessLinkSnapshot link=requireActiveLink(snapshot,candidate.getSourceVehicleId());
            AccessGatewaySnapshot gateway=requireGateway(snapshot,link.getGatewayId());
            if(gateway.getBandwidthPoolId()!=null) return requirePool(snapshot,gateway.getBandwidthPoolId());
        }

        // Compatibilità con il livello 18.2: unico pool globale.
        BandwidthPoolSnapshot global=uniqueGlobalPool(snapshot);
        if(global!=null) return global;

        throw new IllegalArgumentException("Cannot resolve bandwidth pool for remote candidate "+candidate.getCandidateId());
    }

    private BandwidthPoolSnapshot requirePool(SystemSnapshot snapshot,String poolId){for(BandwidthPoolSnapshot p:snapshot.getBandwidthPools())if(poolId.equals(p.getPoolId()))return p;throw new IllegalArgumentException("Missing bandwidth pool: "+poolId);}
    private AccessLinkSnapshot requireActiveLink(SystemSnapshot snapshot,String vehicleId){AccessLinkSnapshot found=null;for(AccessLinkSnapshot l:snapshot.getAccessLinks())if(vehicleId.equals(l.getVehicleId())&&l.isActive()){if(found!=null)throw new IllegalArgumentException("Vehicle "+vehicleId+" has multiple active access links.");found=l;}if(found==null)throw new IllegalArgumentException("Vehicle "+vehicleId+" has no active access link.");return found;}
    private AccessGatewaySnapshot requireGateway(SystemSnapshot snapshot,String gatewayId){for(AccessGatewaySnapshot g:snapshot.getAccessGateways())if(gatewayId.equals(g.getGatewayId()))return g;throw new IllegalArgumentException("Missing gateway: "+gatewayId);}
    private BandwidthPoolSnapshot uniqueGlobalPool(SystemSnapshot snapshot){BandwidthPoolSnapshot found=null;for(BandwidthPoolSnapshot p:snapshot.getBandwidthPools())if(p.getPoolType()==BandwidthPoolType.GLOBAL){if(found!=null)return null;found=p;}return found;}
}
