package android.nfc;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
public class MultiSERoutingInfo implements Parcelable {

    public static byte ROUTE_DEFAULT = 0x00;
    public static byte ROUTE_AID = 0x01;
    public static byte ROUTE_PROTOCOL = 0x02;
    public static byte ROUTE_TECHNOLOGY = 0x03;

    public static byte LOCATION_UICC = 0x01;
    public static byte LOCATION_ESE = 0x02;
    public static byte LOCATION_HOST = 0x04;

    public static byte POWER_STATE_LOW = 0x02;
    public static byte POWER_STATE_FULL = 0x01;
    public static byte POWER_STATE_BOTH = 0x03;

    public static byte PROTOCOL_MIFARE = 0x01;

    private byte mRouteType;
    private int[] mRouteDetail;
    private byte mLocation;
    private byte mPowerState;

    public MultiSERoutingInfo() {

    }

    public MultiSERoutingInfo(byte routeType, int[] routeDetail, byte location, byte powerState) {
        this.mRouteType = routeType;
        this.mRouteDetail = routeDetail;
        this.mLocation = location;
        this.mPowerState = powerState;
    }

    public byte getRouteType() {
        return mRouteType;
    }

    public void setRouteType(byte mRouteType) {
        this.mRouteType = mRouteType;
    }

    public int[] getRouteDetail() {
        return mRouteDetail;
    }

    public void setRouteDetail(int[] mRouteDetail) {
        this.mRouteDetail = mRouteDetail;
    }

    public byte getLocation() {
        return mLocation;
    }

    public void setLocation(byte mLocation) {
        this.mLocation = mLocation;
    }

    public byte getPowerState() {
        return mPowerState;
    }

    public void setPowerState(byte mPowerState) {
        this.mPowerState = mPowerState;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mLocation);
        dest.writeInt(mPowerState);
        dest.writeInt(mRouteType);
        if (null != mRouteDetail) {
            dest.writeInt(mRouteDetail.length);
            dest.writeIntArray(mRouteDetail);
        }
    }

    public static final Parcelable.Creator<MultiSERoutingInfo> CREATOR =
            new Parcelable.Creator<MultiSERoutingInfo>() {
                public MultiSERoutingInfo createFromParcel(Parcel in) {
                    byte location = (byte) in.readInt();
                    byte powerState = (byte) in.readInt();
                    byte routeType = (byte) in.readInt();
                    int routeDetailLength = in.readInt();
                    int[] routeDetail = new int[routeDetailLength];
                    in.readIntArray(routeDetail);

                    return new MultiSERoutingInfo(routeType, routeDetail, location, powerState);
                }

                public MultiSERoutingInfo[] newArray(int size) {
                    return new MultiSERoutingInfo[size];
                }
            };

}
