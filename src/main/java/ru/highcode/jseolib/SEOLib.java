package ru.highcode.jseolib;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ru.highcode.jseolib.model.KeywordGroup;
import ru.highcode.jseolib.model.ProjectData;
import ru.highcode.jseolib.model.ProjectInfo;
import ru.highcode.jseolib.model.ProjectRegion;

public class SEOLib {
    private final String URL_PREFIX = "https://api.seolib.ru/v1/";
    private final String token;
    // Only json for now
    private final Format format = Format.json;
    private final int timeout = 5 * 1000;
    private final Gson gson;

    public SEOLib(String token) {
        this.token = token;
        gson = new GsonBuilder().setDateFormat("yyyy-mm-dd hh:mm:ss").create();
    }

    public List<ProjectData> projectLists() throws IOException {
        final List<ProjectData> result = new ArrayList<>();
        final JsonArray data = loadData(makeUrl("project/list")).getAsJsonArray();
        data.forEach(jd -> result.add(gson.fromJson(jd, ProjectData.class)));
        return result;
    }

    public ProjectInfo projectProjectInfo(long projectId) throws IOException {
        final Map<String, String> params = new HashMap<>();
        params.put("project_id", String.valueOf(projectId));
        final JsonElement data = loadData(makeUrl("project/projectinfo", params));
        return gson.fromJson(data, ProjectInfo.class);
    }

    public List<KeywordGroup> listKeywordGroups(long projectId) throws IOException {
        final Map<String, String> params = new HashMap<>();
        params.put("project_id", String.valueOf(projectId));
        final JsonElement data = loadData(makeUrl("project/list/keyword/groups", params));
        final List<KeywordGroup> result = new ArrayList<>();
        data.getAsJsonArray().forEach(e -> {
            result.add(gson.fromJson(e, KeywordGroup.class));
        });
        return result;
    }

    public List<KeywordGroup> listKeywords(long projectId) throws IOException {
        final Map<String, String> params = new HashMap<>();
        params.put("project_id", String.valueOf(projectId));
        // TODO make good documentation
        params.put("construct", "k");
        final JsonElement data = loadData(makeUrl("project/list/keyword/groups", params));
        final List<KeywordGroup> result = new ArrayList<>();
        System.out.println(data);
        data.getAsJsonArray().forEach(e -> {
            result.add(gson.fromJson(e, KeywordGroup.class));
        });
        return result;
    }

    // links/data/get
    public void linksDataGet() {
    }

    // TODO better fit model
    public Map<String, Map<Integer, ProjectRegion>> projectRegions(long projectId) throws IOException {
        return projectRegions(projectId, true);
    }

    public Map<String, Map<Integer, ProjectRegion>> projectRegions(long projectId, boolean onlyActive)
            throws IOException {
        final Map<String, String> params = new HashMap<>();
        params.put("project_id", String.valueOf(projectId));
        if (onlyActive) {
            params.put("only_active", String.valueOf(onlyActive));
        }
        final JsonElement data = loadData(makeUrl("project/regions", params));
        final JsonObject regionsMap = data.getAsJsonObject();

        final Map<String, Map<Integer, ProjectRegion>> result = new HashMap<>();

        regionsMap.entrySet().forEach(e -> {
            try {
                // skip numbers
                Integer.parseInt(e.getKey());
                return;
            } catch (final NumberFormatException nan) {
            }

            e.getValue().getAsJsonObject();
            result.put(e.getKey(), makeRegionsMap(e.getValue().getAsJsonObject().getAsJsonObject("regions")));
        });

        return result;
    }

    private Map<Integer, ProjectRegion> makeRegionsMap(JsonObject json) {
        final Map<Integer, ProjectRegion> result = new HashMap<>();
        json.entrySet().forEach(e -> {
            final Integer key = Integer.parseInt(e.getKey());
            final ProjectRegion value = gson.fromJson(e.getValue(), ProjectRegion.class);
            result.put(key, value);
        });
        return result;
    }

    // TODO: project/history/positions/by/date
    // TODO: project/history/positions/by/daterange

    // TODO: links/data/create
    // TODO: project/creates

    private String makeUrl(String api) {
        return makeUrl(api, new HashMap<>());
    }

    private String makeUrl(String api, Map<String, String> params) {
        final StringBuilder sb = new StringBuilder();
        sb.append(URL_PREFIX).append(api).append(".").append(format.name()).append("?");
        params.put("access_token", token);
        params.put("format", format.name());
        final String paramString = params.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> String.join("&", a, b)).orElse("");
        sb.append(paramString);

        return sb.toString();
    }

    // TODO: project/regions
    enum Format {
        jsonp, json, xml, rss, html;
    }

    private static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[] { new X509TrustManager() {
        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
            // no-op
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
            // no-op
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    } };

    private void setupSSL(HttpsURLConnection connection) throws IOException {
        try {
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, TRUST_ALL_CERTS, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            connection.setSSLSocketFactory(sslSocketFactory);
        } catch (final Exception e) {
            throw new IOException(e);
        }

    }

    private JsonElement loadData(String url) throws IOException, MalformedURLException {
        final URLConnection connection = new URL(url).openConnection();
        setupSSL((HttpsURLConnection) connection);
        connection.setConnectTimeout(timeout);
        final JsonObject json = new JsonParser().parse(new InputStreamReader(connection.getInputStream()))
                .getAsJsonObject();
        final int statusCodes = json.get("statusCodes").getAsInt();

        if (statusCodes == 200) {
            return json.get("data");
        } else {
            throw new IOException("statusCodes: " + statusCodes);
        }
    }
}
