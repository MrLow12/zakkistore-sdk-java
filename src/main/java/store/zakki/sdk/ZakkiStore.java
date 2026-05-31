package store.zakki.sdk;

import com.google.gson.Gson;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class ZakkiStore {
    private String baseUrl;
    private String token;
    private String iduser;
    private String email;
    private String pin;
    private boolean autoWithdraw;

    public ZakkiStore(String token) {
        this("https://qris.zakki.store", token, null, null, null, false);
    }

    public ZakkiStore(String baseUrlOrToken, String token) {
        this(baseUrlOrToken, token, null, null, null, false);
    }

    @SuppressWarnings("unchecked")
    public ZakkiStore(String baseUrlOrToken, String token, String iduser, String email, String pin, boolean autoWithdraw) {
        String finalBaseUrl = baseUrlOrToken;
        String finalToken = token;

        // Smart detection if token is placed in base_url parameter
        if (baseUrlOrToken != null && !baseUrlOrToken.startsWith("http://") && !baseUrlOrToken.startsWith("https://") && token == null) {
            finalToken = baseUrlOrToken;
            finalBaseUrl = "https://qris.zakki.store";
        }

        if (finalToken == null || finalToken.isEmpty()) {
            throw new IllegalArgumentException("token wajib disertakan dalam konfigurasi SDK.");
        }
        if (finalBaseUrl == null || finalBaseUrl.isEmpty()) {
            throw new IllegalArgumentException("base_url wajib disertakan dalam konfigurasi SDK.");
        }

        if (finalBaseUrl.endsWith("/")) {
            finalBaseUrl = finalBaseUrl.substring(0, finalBaseUrl.length() - 1);
        }

        this.baseUrl = finalBaseUrl;
        this.token = finalToken;
        this.iduser = iduser;
        this.email = email;
        this.pin = pin;
        this.autoWithdraw = autoWithdraw;
    }

    public void enableAutoWithdraw(boolean status) {
        this.autoWithdraw = status;
    }

    public void enable_auto_withdraw(boolean status) {
        this.autoWithdraw = status;
    }

    // ==========================================================
    // --- 1. PAYMENT GATEWAY (QRIS TOPUP) ---
    // ==========================================================

    public Map<String, Object> topup(int nominal) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("nominal", nominal);
        return _request("/topup", "POST", payload);
    }

    public Map<String, Object> cektopup(String idtopup) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("idtopup", idtopup);
        return _request("/cektopup", "GET", payload);
    }

    public Map<String, Object> cancel() {
        return cancel(null, false);
    }

    public Map<String, Object> cancel(String idTransaksi) {
        return cancel(idTransaksi, false);
    }

    public Map<String, Object> cancel(boolean allPending) {
        return cancel(null, allPending);
    }

    public Map<String, Object> cancel(String idTransaksi, boolean allPending) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        if (idTransaksi != null && !idTransaksi.isEmpty()) {
            payload.put("id_transaksi", idTransaksi);
        }
        if (allPending) {
            payload.put("all", true);
        }
        return _request("/cancel", "POST", payload);
    }

    // ==========================================================
    // --- 2. TRANSAKSI H2H (HOST-TO-HOST) ---
    // ==========================================================

    public Map<String, Object> listkode() {
        return listkode(null, null);
    }

    public Map<String, Object> listkode(String jenis) {
        return listkode(jenis, null);
    }

    public Map<String, Object> listkode(String jenis, String productType) {
        Map<String, Object> payload = new HashMap<>();
        if (jenis != null && !jenis.isEmpty()) {
            payload.put("jenis", jenis);
        }
        if (productType != null && !productType.isEmpty()) {
            payload.put("type", productType);
        }
        return _request("/listkode", "GET", payload);
    }

    public Map<String, Object> h2h(Map<String, Object> params) {
        String kode = (String) params.get("kode");
        String tujuan = (String) params.get("tujuan");
        String refID = (String) params.get("refID");
        return h2h(kode, tujuan, refID);
    }

    public Map<String, Object> h2h(String kode) {
        return h2h(kode, null, null);
    }

    public Map<String, Object> h2h(String kode, String tujuan) {
        return h2h(kode, tujuan, null);
    }

    public Map<String, Object> h2h(String kode, String tujuan, String refID) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("kode", kode);
        payload.put("tujuan", tujuan);
        payload.put("refID", refID);
        return _request("/h2h", "POST", payload);
    }

    public Map<String, Object> cekh2h(String idTrx) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", idTrx);
        return _request("/cekh2h", "GET", payload);
    }

    public Map<String, Object> myh2h() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        return _request("/myh2h", "GET", payload);
    }

    // ==========================================================
    // --- 3. PERBANKAN & TRANSFER SALDO ---
    // ==========================================================

    @SuppressWarnings("unchecked")
    public Map<String, Object> checkbank() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        if (this.iduser != null && !this.iduser.isEmpty()) {
            payload.put("iduser", this.iduser);
        } else if (this.email != null && !this.email.isEmpty()) {
            payload.put("email", this.email);
        }

        Map<String, Object> bankRes = _request("/checkbank", "GET", payload);

        if (this.autoWithdraw && bankRes != null && bankRes.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) bankRes.get("data");
            if (data != null && data.containsKey("bank_detail")) {
                Map<String, Object> bankDetail = (Map<String, Object>) data.get("bank_detail");
                if (bankDetail != null && bankDetail.containsKey("balance")) {
                    double balance = 0;
                    try {
                        balance = Double.parseDouble(bankDetail.get("balance").toString());
                    } catch (Exception e) {
                        // ignore
                    }

                    if (balance > 0) {
                        try {
                            Map<String, Object> withdrawRes = tarik((int) balance);
                            bankRes = _request("/checkbank", "GET", payload);
                            bankRes.put("auto_withdraw_executed", true);
                            bankRes.put("auto_withdraw_amount", (int) balance);
                            bankRes.put("auto_withdraw_message", withdrawRes.containsKey("message") ? withdrawRes.get("message").toString() : "Auto-withdraw berhasil dijalankan.");
                        } catch (Exception e) {
                            bankRes.put("auto_withdraw_executed", false);
                            bankRes.put("auto_withdraw_error", e.getMessage());
                        }
                    }
                }
            }
        }

        return bankRes;
    }

    public Map<String, Object> checkname(String number) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("number", number != null ? number.trim() : "");
        return _request("/checkname", "GET", payload);
    }

    public Map<String, Object> transfer(Map<String, Object> params) {
        String to = (String) params.get("to");
        int amount = 0;
        if (params.get("amount") != null) {
            amount = ((Number) params.get("amount")).intValue();
        }
        return transfer(to, amount);
    }

    public Map<String, Object> transfer(String to, int amount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("to", to);
        payload.put("amount", amount);
        return _request("/transfer", "POST", payload);
    }

    public Map<String, Object> tabung(int jumlah) {
        if (this.pin == null || this.pin.isEmpty()) {
            throw new RuntimeException("[ZakkiStore SDK Error] PIN transaksi diperlukan untuk melakukan transaksi tabung.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("jumlah", jumlah);
        payload.put("pin", this.pin);
        if (this.iduser != null && !this.iduser.isEmpty()) {
            payload.put("iduser", this.iduser);
        }
        if (this.email != null && !this.email.isEmpty()) {
            payload.put("email", this.email);
        }
        return _request("/tabung", "POST", payload);
    }

    public Map<String, Object> tarik(int jumlah) {
        if (this.pin == null || this.pin.isEmpty()) {
            throw new RuntimeException("[ZakkiStore SDK Error] PIN transaksi diperlukan untuk melakukan transaksi tarik.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("jumlah", jumlah);
        payload.put("pin", this.pin);
        if (this.iduser != null && !this.iduser.isEmpty()) {
            payload.put("iduser", this.iduser);
        }
        if (this.email != null && !this.email.isEmpty()) {
            payload.put("email", this.email);
        }
        return _request("/tarik", "POST", payload);
    }

    public Map<String, Object> checkmutasi() {
        return checkmutasi("all");
    }

    public Map<String, Object> checkmutasi(String mutasiType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("type", mutasiType);
        if (this.iduser != null && !this.iduser.isEmpty()) {
            payload.put("iduser", this.iduser);
        }
        if (this.email != null && !this.email.isEmpty()) {
            payload.put("email", this.email);
        }
        return _request("/checkmutasi", "GET", payload);
    }

    // ==========================================================
    // --- 4. NOKTEL MARKETPLACE (OTP VIRTUAL) ---
    // ==========================================================

    public Map<String, Object> noktelStok() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        return _request("/noktel/stok", "GET", payload);
    }

    public Map<String, Object> noktelBuy(String category) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("category", category != null ? category.trim() : "");
        return _request("/noktel/buy", "POST", payload);
    }

    public Map<String, Object> noktelGetOtp(String accountId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("account_id", accountId != null ? accountId.trim() : "");
        return _request("/noktel/getotp", "GET", payload);
    }

    public Map<String, Object> noktelCancel(String invoiceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("invoice_id", invoiceId != null ? invoiceId.trim() : "");
        return _request("/noktel/cancel", "POST", payload);
    }

    public Map<String, Object> noktelHistory() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        return _request("/noktel/history", "GET", payload);
    }

    // ==========================================================
    // --- 5. REWARD KOMPUTASI & UTILITY ---
    // ==========================================================

    public Map<String, Object> cekmining() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        return _request("/cekmining", "GET", payload);
    }

    public Map<String, Object> mymining() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        return _request("/mymining", "GET", payload);
    }

    public Map<String, Object> cekgacha() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        return _request("/cekgacha", "GET", payload);
    }

    public Map<String, Object> whitelistip(String ip) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("ip", ip != null ? ip.trim() : "");
        return _request("/whitelistip", "POST", payload);
    }

    public Map<String, Object> delwhitelistip(String ip) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", this.token);
        payload.put("ip", ip != null ? ip.trim() : "");
        return _request("/delwhitelistip", "POST", payload);
    }

    public Map<String, Object> leaderboard() {
        return leaderboard(10, "all");
    }

    public Map<String, Object> leaderboard(int limit) {
        return leaderboard(limit, "all");
    }

    public Map<String, Object> leaderboard(int limit, String period) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("limit", limit);
        payload.put("period", period != null ? period.trim() : "");
        return _request("/leaderboard", "GET", payload);
    }

    public Map<String, Object> status() {
        return _request("/status", "GET", null);
    }

    // ==========================================================
    // --- CORE REQUEST HANDLER ---
    // ==========================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> _request(String endpoint, String method, Map<String, Object> data) {
        HttpURLConnection conn = null;
        try {
            String urlStr = this.baseUrl + endpoint;
            
            if ("GET".equalsIgnoreCase(method) && data != null && !data.isEmpty()) {
                StringBuilder query = new StringBuilder();
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (entry.getValue() != null) {
                        if (query.length() > 0) query.append("&");
                        query.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                             .append("=")
                             .append(URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
                    }
                }
                if (query.length() > 0) {
                    urlStr += "?" + query.toString();
                }
            }

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method.toUpperCase());
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoInput(true);

            if (!"GET".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                if (data != null && !data.isEmpty()) {
                    String jsonInputString = new Gson().toJson(data);
                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonInputString.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }
                }
            }

            int code = conn.getResponseCode();
            
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseStr = "";
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    responseStr = response.toString();
                }
            }

            Map<String, Object> resJson = new HashMap<>();
            try {
                resJson = new Gson().fromJson(responseStr, Map.class);
            } catch (Exception e) {
                resJson.put("message", responseStr);
            }

            if (code < 200 || code >= 300) {
                String errMsg = resJson.containsKey("message") ? resJson.get("message").toString() : "HTTP Error! Status: " + code;
                if (code == 403 || errMsg.toLowerCase().contains("ip")) {
                    errMsg += "\n⚠️ [IP BLOCKED / UNREGISTERED] IP Anda diblokir atau belum terdaftar di whitelist API. Silakan hubungi developer via WhatsApp (https://wa.me/6283844082339) or Telegram (https://t.me/zakki_store) untuk mendapatkan bantuan.";
                }
                throw new RuntimeException("[ZakkiStore SDK Error] " + errMsg);
            }

            return resJson;
        } catch (IOException e) {
            throw new RuntimeException("[ZakkiStore SDK Error] Koneksi Gagal: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
