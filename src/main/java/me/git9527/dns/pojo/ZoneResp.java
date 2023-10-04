package me.git9527.dns.pojo;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ZoneResp extends CloudFlareResp {
    
    @SerializedName("result")
    private List<ZoneInfo> zoneInfos;
}
