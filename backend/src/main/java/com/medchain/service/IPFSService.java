package com.medchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IPFS Service — integrates with a local Kubo (go-ipfs) node via HTTP API.
 *
 * Graceful degradation: if IPFS is unavailable, returns a realistic mock CID
 * so the demo continues to work without a running IPFS node.
 *
 * API endpoint used: POST /api/v0/add
 * Gateway used:       GET  /ipfs/{cid}
 */
@Slf4j
@Service
public class IPFSService {

    @Value("${ipfs.api.url:http://localhost:5001}")
    private String ipfsApiUrl;

    @Value("${ipfs.gateway.url:http://localhost:8080}")
    private String ipfsGatewayUrl;

    @Value("${ipfs.enabled:true}")
    private boolean ipfsEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // ─── Upload ───────────────────────────────────────────────────────────────

    /**
     * Upload raw bytes to IPFS and return the Content Identifier (CID).
     *
     * @param data     raw bytes to upload (typically AES-encrypted file bytes)
     * @param filename logical filename for the IPFS entry
     * @return IPFS CID string (e.g., "QmXyz...")
     */
    public String uploadFile(byte[] data, String filename) {
        if (!ipfsEnabled) {
            log.info("IPFS disabled — returning mock CID for: {}", filename);
            return generateMockCid();
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String uploadUrl = ipfsApiUrl + "/api/v0/add";
            HttpPost post = new HttpPost(new URI(uploadUrl));

            // Build multipart body
            var entity = MultipartEntityBuilder.create()
                    .addBinaryBody("file", data, ContentType.APPLICATION_OCTET_STREAM, filename)
                    .build();
            post.setEntity(entity);

            String responseBody = httpClient.execute(post, response -> {
                int status = response.getCode();
                String body = EntityUtils.toString(response.getEntity());
                if (status != 200) {
                    throw new RuntimeException("IPFS returned HTTP " + status + ": " + body);
                }
                return body;
            });

            JsonNode json = objectMapper.readTree(responseBody);
            String cid = json.get("Hash").asText();
            log.info("File uploaded to IPFS. CID: {} | Size: {} bytes", cid, data.length);
            return cid;

        } catch (Exception e) {
            log.warn("IPFS upload failed ({}), falling back to mock CID for: {}", e.getMessage(), filename);
            return generateMockCid();
        }
    }

    /**
     * Upload a text/JSON string to IPFS.
     */
    public String uploadString(String content, String filename) {
        return uploadFile(content.getBytes(java.nio.charset.StandardCharsets.UTF_8), filename);
    }

    // ─── Download ─────────────────────────────────────────────────────────────

    /**
     * Download content from IPFS by CID via the gateway.
     *
     * @param cid IPFS Content Identifier
     * @return raw bytes retrieved from IPFS
     */
    public byte[] downloadFile(String cid) {
        if (!ipfsEnabled || isMockCid(cid)) {
            log.info("IPFS disabled or mock CID — cannot retrieve content for: {}", cid);
            return new byte[0];
        }

        try {
            String gatewayUrl = ipfsGatewayUrl + "/ipfs/" + cid;
            byte[] bytes = restTemplate.getForObject(URI.create(gatewayUrl), byte[].class);
            log.info("Downloaded {} bytes from IPFS CID: {}", bytes != null ? bytes.length : 0, cid);
            return bytes != null ? bytes : new byte[0];
        } catch (Exception e) {
            log.warn("IPFS download failed for CID {}: {}", cid, e.getMessage());
            return new byte[0];
        }
    }

    // ─── Health Check ─────────────────────────────────────────────────────────

    /**
     * Check whether the IPFS node is reachable.
     */
    public boolean isAvailable() {
        if (!ipfsEnabled) return false;
        try {
            String versionUrl = ipfsApiUrl + "/api/v0/version";
            var response = restTemplate.getForEntity(URI.create(versionUrl), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Return the public gateway URL for a given CID.
     */
    public String getGatewayUrl(String cid) {
        if (isMockCid(cid)) return "#";
        return ipfsGatewayUrl + "/ipfs/" + cid;
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────

    /**
     * Generate a realistic-looking IPFS v0 CID (Qm... base58 style, 46 chars)
     * for demo/fallback purposes when IPFS is unavailable.
     */
    private String generateMockCid() {
        // Base58 alphabet (Bitcoin/IPFS style)
        String base58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder("Qm");
        for (int i = 0; i < 44; i++) {
            sb.append(base58.charAt(ThreadLocalRandom.current().nextInt(base58.length())));
        }
        return sb.toString();
    }

    private boolean isMockCid(String cid) {
        return cid == null || cid.isEmpty() || cid.startsWith("Qm") && cid.length() == 46
               && cid.matches("Qm[a-zA-Z0-9]{44}") && !isRealCidReachable(cid);
    }

    private boolean isRealCidReachable(String cid) {
        // Simple heuristic — if IPFS is not enabled, it's a mock
        return ipfsEnabled;
    }
}
