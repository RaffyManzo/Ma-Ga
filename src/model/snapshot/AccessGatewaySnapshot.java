package model.snapshot;

/** Punto di accesso radio osservato nello scenario. */
public final class AccessGatewaySnapshot {
    private final String gatewayId;
    private final String gatewayType;
    private final double x;
    private final double y;
    private final double coverageRadiusMeters;
    private final String bandwidthPoolId;

    /** Costruttore storico compatibile con il livello gateway precedente. */
    public AccessGatewaySnapshot(String gatewayId,String gatewayType,double x,double y,double coverageRadiusMeters) {
        this(gatewayId,gatewayType,x,y,coverageRadiusMeters,null);
    }

    public AccessGatewaySnapshot(String gatewayId,String gatewayType,double x,double y,double coverageRadiusMeters,String bandwidthPoolId) {
        this.gatewayId=requireText(gatewayId,"gatewayId"); this.gatewayType=requireText(gatewayType,"gatewayType");
        this.x=requireFinite(x,"x"); this.y=requireFinite(y,"y"); this.coverageRadiusMeters=requirePositive(coverageRadiusMeters,"coverageRadiusMeters");
        this.bandwidthPoolId=normalizeOptional(bandwidthPoolId);
    }
    public String getGatewayId(){return gatewayId;} public String getGatewayType(){return gatewayType;} public double getX(){return x;} public double getY(){return y;} public double getCoverageRadiusMeters(){return coverageRadiusMeters;} public String getBandwidthPoolId(){return bandwidthPoolId;}
    private static String normalizeOptional(String v){return v==null||v.isBlank()?null:v;}
    private static String requireText(String v,String f){if(v==null||v.isBlank())throw new IllegalArgumentException(f+" must not be null or blank.");return v;}
    private static double requireFinite(double v,String f){if(!Double.isFinite(v))throw new IllegalArgumentException(f+" must be finite.");return v;}
    private static double requirePositive(double v,String f){requireFinite(v,f);if(v<=0)throw new IllegalArgumentException(f+" must be > 0.");return v;}
}
