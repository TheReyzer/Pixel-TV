package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Google device login plus one Drive file per linked profile. Local data remains the source of truth offline. */
final class GoogleProfileSync {
    private static final String DRIVE_SCOPE="https://www.googleapis.com/auth/drive.file";
    private static final String SCOPES="openid profile email "+DRIVE_SCOPE;
    private static final String DEVICE_ENDPOINT="https://oauth2.googleapis.com/device/code";
    private static final String TOKEN_ENDPOINT="https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT="https://openidconnect.googleapis.com/v1/userinfo";
    private static final String DRIVE_ENDPOINT="https://www.googleapis.com/drive/v3/files";
    private static final String UPLOAD_ENDPOINT="https://www.googleapis.com/upload/drive/v3/files";
    private static final String FOLDER_NAME="PIXEL TV";
    private static final String LEGACY_FOLDER_NAME="TV VibeCheck";
    private static final String FILE_NAME="perfil.json";
    private static final String FOLDER_MIME="application/vnd.google-apps.folder";
    private static final int MAX_BODY=1_000_000;

    interface LinkListener {
        void code(String url,String userCode);
        void status(String message);
        void success(String email);
        void error(String message);
    }

    final class LinkHandle {
        private final AtomicBoolean cancelled=new AtomicBoolean();
        void cancel() { cancelled.set(true); }
        boolean cancelled() { return cancelled.get()||destroyed; }
    }

    private final Activity activity;
    private final ViewerProfiles model;
    private final Runnable localChanged;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean syncing=new AtomicBoolean();
    private final AtomicBoolean dirty=new AtomicBoolean();
    private final AtomicBoolean activeLink=new AtomicBoolean();
    private volatile boolean destroyed;

    GoogleProfileSync(Activity activity,ViewerProfiles model,Runnable localChanged) {
        this.activity=activity; this.model=model; this.localChanged=localChanged;
        prefs=activity.getSharedPreferences("google_profile_sync_v1",Context.MODE_PRIVATE);
    }

    boolean configured() { return !BuildConfig.GOOGLE_CLIENT_ID.trim().isEmpty(); }
    boolean linked(String profileId) { return !refreshKey(profileId).isEmpty(); }
    String email(String profileId) { return prefs.getString("email_"+profileId,""); }
    String status(String profileId) { return linked(profileId)?"Google · "+(email(profileId).isEmpty()?"vinculado":email(profileId)):"Não vinculado"; }
    void destroy() { destroyed=true; worker.shutdownNow(); }

    LinkHandle link(String profileId,LinkListener listener) {
        LinkHandle handle=new LinkHandle();
        if (!configured()) { post(() -> listener.error("Configure o client ID Google no build para ativar a sincronização.")); return handle; }
        if (!activeLink.compareAndSet(false,true)) { post(() -> listener.error("Já existe uma vinculação em andamento.")); return handle; }
        worker.execute(() -> {
            try {
                DeviceCode device=requestDeviceCode();
                post(() -> listener.code(device.url,device.userCode));
                Token token=poll(device,handle,listener);
                if (handle.cancelled()) return;
                JSONObject user=getJson(USERINFO_ENDPOINT,token.accessToken);
                String sub=user.optString("sub"); if (sub.isEmpty()) throw new IOException("O Google não retornou a identidade da conta.");
                String already=linkedProfileForSub(sub,profileId); if (already!=null) throw new IOException("Esta conta Google já está vinculada a outro perfil.");
                String email=user.optString("email","");
                saveRefresh(profileId,token.refreshToken,sub,email);
                synchronizeOne(profileId,token.accessToken);
                post(() -> listener.success(email.isEmpty()?"conta Google":email));
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception e) { if (!handle.cancelled()) post(() -> listener.error(userMessage(e))); }
            finally { activeLink.set(false); }
        });
        return handle;
    }

    void unlink(String profileId) {
        prefs.edit().remove("refresh_"+profileId).remove("email_"+profileId).remove("sub_"+profileId).apply();
        try { KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null); String alias=alias(profileId); if (ks.containsAlias(alias)) ks.deleteEntry(alias); }
        catch (Exception ignored) { }
    }

    void syncAll() {
        if (!configured()||destroyed) return;
        dirty.set(true);
        if (!syncing.compareAndSet(false,true)) return;
        worker.execute(() -> {
            try {
                do { dirty.set(false); for (String id:linkedIds()) if (!destroyed) try { synchronizeOne(id,null); } catch (Exception e) { android.util.Log.w("TVHomeSync","profile="+id,e); } }
                while (dirty.get()&&!destroyed);
            } finally { syncing.set(false); if (dirty.get()&&!destroyed) syncAll(); }
        });
    }

    private void synchronizeOne(String profileId,String accessToken) throws Exception {
        if (!linked(profileId)) return;
        String token=accessToken==null?refreshAccessToken(refreshKey(profileId)):accessToken;
        String local=model.profileJson(profileId);
        String folderId=findOrCreateFolder(token);
        String fileId=findFile(token,folderId);
        String remote=fileId==null?null:downloadFile(token,fileId);
        if (remote!=null) {
            model.mergeProfileJson(profileId,remote);
            local=model.profileJson(profileId);
            post(localChanged);
        }
        if (fileId==null) createFile(token,folderId,local); else updateFile(token,fileId,local);
    }

    private Set<String> linkedIds() {
        Set<String> ids=new HashSet<>(prefs.getStringSet("linked",Collections.emptySet()));
        for (ViewerProfiles.Profile p:model.profiles()) if (!p.aggregate()&&linked(p.id)) ids.add(p.id);
        return ids;
    }

    private DeviceCode requestDeviceCode() throws Exception {
        JSONObject o=postForm(DEVICE_ENDPOINT,form("client_id",BuildConfig.GOOGLE_CLIENT_ID,"scope",SCOPES));
        return new DeviceCode(o.getString("device_code"),o.getString("user_code"),o.getString("verification_url"),o.optInt("interval",5),o.optInt("expires_in",1800));
    }

    private Token poll(DeviceCode device,LinkHandle handle,LinkListener listener) throws Exception {
        long deadline=System.currentTimeMillis()+device.expiresIn*1000L; int interval=device.interval;
        while (System.currentTimeMillis()<deadline&&!handle.cancelled()) {
            Thread.sleep(interval*1000L);
            // Google answers with HTTP 428 while the user has not approved the device yet.
            // That is a normal state of the device flow, not a link failure.
            JSONObject response=postTokenForm(oauthForm("device_code",device.deviceCode,"grant_type","urn:ietf:params:oauth:grant-type:device_code"));
            String error=response.optString("error");
            if ("authorization_pending".equals(error)) { post(() -> listener.status("Aguardando aprovação no celular…")); continue; }
            if ("slow_down".equals(error)) { interval+=5; continue; }
            if (!error.isEmpty()) throw new IOException(error);
            String access=response.optString("access_token"), refresh=response.optString("refresh_token");
            if (access.isEmpty()||refresh.isEmpty()) throw new IOException("O Google não retornou os tokens necessários.");
            return new Token(access,refresh);
        }
        throw new IOException("O código expirou ou a vinculação foi cancelada.");
    }

    private String refreshAccessToken(String refresh) throws Exception {
        JSONObject response=postForm(TOKEN_ENDPOINT,oauthForm("refresh_token",refresh,"grant_type","refresh_token"));
        String token=response.optString("access_token"); if (token.isEmpty()) throw new IOException("A sessão Google expirou. Vincule o perfil novamente."); return token;
    }

    private String findOrCreateFolder(String token) throws Exception {
        String query="name='"+quote(FOLDER_NAME)+"' and mimeType='"+FOLDER_MIME+"' and trashed=false";
        JSONObject listed=list(token,query,"files(id,name)"); if (listed.getJSONArray("files").length()>0) return listed.getJSONArray("files").getJSONObject(0).getString("id");
        String legacyQuery="name='"+quote(LEGACY_FOLDER_NAME)+"' and mimeType='"+FOLDER_MIME+"' and trashed=false";
        JSONObject legacyListed=list(token,legacyQuery,"files(id,name)");
        if (legacyListed.getJSONArray("files").length()>0) {
            String legacyId=legacyListed.getJSONArray("files").getJSONObject(0).getString("id");
            try { requestText(DRIVE_ENDPOINT+"/"+enc(legacyId),"PATCH",token,new JSONObject().put("name",FOLDER_NAME).toString(),"application/json"); } catch (Exception ignored) { }
            return legacyId;
        }
        JSONObject meta=new JSONObject().put("name",FOLDER_NAME).put("mimeType",FOLDER_MIME);
        return postJson(DRIVE_ENDPOINT,meta.toString(),token).getString("id");
    }

    private String findFile(String token,String folderId) throws Exception {
        JSONObject listed=list(token,"name='"+quote(FILE_NAME)+"' and '"+quote(folderId)+"' in parents and trashed=false","files(id,name)");
        return listed.getJSONArray("files").length()==0?null:listed.getJSONArray("files").getJSONObject(0).getString("id");
    }

    private JSONObject list(String token,String query,String fields) throws Exception {
        String url=DRIVE_ENDPOINT+"?q="+enc(query)+"&spaces=drive&fields="+enc(fields);
        return getJson(url,token);
    }

    private String downloadFile(String token,String id) throws Exception { return requestText(DRIVE_ENDPOINT+"/"+enc(id)+"?alt=media","GET",token,null,"application/json"); }

    private void createFile(String token,String folder,String data) throws Exception {
        JSONObject meta=new JSONObject().put("name",FILE_NAME).put("parents",new org.json.JSONArray().put(folder)).put("mimeType","application/json");
        multipart(token,"POST",UPLOAD_ENDPOINT+"?uploadType=multipart",meta.toString(),data);
    }

    private void updateFile(String token,String id,String data) throws Exception { requestText(UPLOAD_ENDPOINT+"/"+enc(id)+"?uploadType=media","PATCH",token,data,"application/json"); }

    private void saveRefresh(String profileId,String refresh,String sub,String email) throws Exception {
        prefs.edit().putString("refresh_"+profileId,encrypt(profileId,refresh)).putString("sub_"+profileId,sub).putString("email_"+profileId,email).putStringSet("linked",union(prefs.getStringSet("linked",Collections.emptySet()),profileId)).apply();
    }

    private Set<String> union(Set<String> before,String id) { Set<String> out=new HashSet<>(before); out.add(id); return out; }
    private String linkedProfileForSub(String sub,String except) { for (String id:linkedIds()) if (!id.equals(except)&&sub.equals(prefs.getString("sub_"+id,""))) return id; return null; }
    private String refreshKey(String id) { String value=prefs.getString("refresh_"+id,""); if (value.isEmpty()) return ""; try { return decrypt(id,value); } catch (Exception e) { return ""; } }

    private String encrypt(String profileId,String value) throws Exception {
        SecretKey key=key(profileId); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key); byte[] iv=cipher.getIV(), body=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); byte[] all=new byte[iv.length+body.length]; System.arraycopy(iv,0,all,0,iv.length); System.arraycopy(body,0,all,iv.length,body.length); return Base64.encodeToString(all,Base64.NO_WRAP);
    }
    private String decrypt(String profileId,String encoded) throws Exception {
        byte[] all=Base64.decode(encoded,Base64.NO_WRAP); byte[] iv=new byte[12]; System.arraycopy(all,0,iv,0,iv.length); byte[] body=new byte[all.length-iv.length]; System.arraycopy(all,iv.length,body,0,body.length); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(profileId),new GCMParameterSpec(128,iv)); return new String(cipher.doFinal(body),StandardCharsets.UTF_8);
    }
    private SecretKey key(String profileId) throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null); String alias=alias(profileId);
        if (!ks.containsAlias(alias)) { android.security.keystore.KeyGenParameterSpec spec=new android.security.keystore.KeyGenParameterSpec.Builder(alias,android.security.keystore.KeyProperties.PURPOSE_ENCRYPT|android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build(); KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore"); generator.init(spec); generator.generateKey(); ks.load(null); }
        return ((KeyStore.SecretKeyEntry)ks.getEntry(alias,null)).getSecretKey();
    }
    private String alias(String id) { return "tv_vibehack_google_"+id.replaceAll("[^A-Za-z0-9]","_"); }

    private void multipart(String token,String method,String url,String metadata,String data) throws Exception {
        String boundary="tvvibehack_"+System.nanoTime(); HttpURLConnection c=open(url,method,token); c.setRequestProperty("Content-Type","multipart/related; boundary="+boundary); c.setDoOutput(true);
        try (OutputStream out=c.getOutputStream()) { write(out,"--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+metadata+"\r\n--"+boundary+"\r\nContent-Type: application/json\r\n\r\n"+data+"\r\n--"+boundary+"--\r\n"); }
        read(c);
    }

    private JSONObject postForm(String url,String body) throws Exception { return new JSONObject(requestText(url,"POST",null,body,"application/x-www-form-urlencoded")); }
    private JSONObject postTokenForm(String body) throws Exception {
        HttpURLConnection c=open(TOKEN_ENDPOINT,"POST",null); c.setRequestProperty("Content-Type","application/x-www-form-urlencoded"); c.setDoOutput(true); write(c.getOutputStream(),body);
        int code=c.getResponseCode(); String response=readBody(c);
        if (code>=200&&code<300 || code==400 || code==403 || code==428) return new JSONObject(response);
        throw new IOException("Google HTTP "+code+": "+response);
    }
    private JSONObject postJson(String url,String body,String token) throws Exception { return new JSONObject(requestText(url,"POST",token,body,"application/json")); }
    private JSONObject getJson(String url,String token) throws Exception { return new JSONObject(requestText(url,"GET",token,null,null)); }
    private String requestText(String url,String method,String token,String body,String contentType) throws Exception {
        HttpURLConnection c=open(url,method,token); if (contentType!=null) c.setRequestProperty("Content-Type",contentType); if (body!=null) { c.setDoOutput(true); write(c.getOutputStream(),body); }
        return read(c);
    }
    private HttpURLConnection open(String url,String method,String token) throws Exception { HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setRequestMethod(method); c.setConnectTimeout(12000); c.setReadTimeout(20000); c.setUseCaches(false); if (token!=null) c.setRequestProperty("Authorization","Bearer "+token); return c; }
    private static String read(HttpURLConnection c) throws Exception { try { int code=c.getResponseCode(); InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream(); String body=read(in); if (code<200||code>=300) throw new IOException("Google HTTP "+code+": "+body); return body; } finally { c.disconnect(); } }
    private static String readBody(HttpURLConnection c) throws Exception { try { int code=c.getResponseCode(); return read(code>=200&&code<300?c.getInputStream():c.getErrorStream()); } finally { c.disconnect(); } }
    private static String read(InputStream in) throws Exception { if (in==null) return ""; ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int total=0,n; while ((n=in.read(buffer))!=-1) { total+=n; if (total>MAX_BODY) throw new IOException("Resposta Google muito grande."); out.write(buffer,0,n); } return out.toString(StandardCharsets.UTF_8.name()); }
    private static void write(OutputStream out,String value) throws IOException { out.write(value.getBytes(StandardCharsets.UTF_8)); out.flush(); }
    private static String form(String... values) throws Exception { List<String> parts=new ArrayList<>(); for (int i=0;i<values.length;i+=2) parts.add(enc(values[i])+"="+enc(values[i+1])); return String.join("&",parts); }
    private static String oauthForm(String... values) throws Exception { List<String> all=new ArrayList<>(); all.add("client_id"); all.add(BuildConfig.GOOGLE_CLIENT_ID); Collections.addAll(all,values); String body=form(all.toArray(new String[0])); if (!BuildConfig.GOOGLE_CLIENT_SECRET.isEmpty()) body+="&"+form("client_secret",BuildConfig.GOOGLE_CLIENT_SECRET); return body; }
    private static String enc(String value) throws Exception { return URLEncoder.encode(value,StandardCharsets.UTF_8.name()); }
    private static String quote(String value) { return value.replace("\\","\\\\").replace("'","\\'"); }
    private void post(Runnable runnable) { if (!destroyed) main.post(runnable); }
    private static String userMessage(Exception e) { String text=e.getMessage(); return text==null||text.isEmpty()?"Não foi possível sincronizar com o Google.":text; }

    private static final class DeviceCode { final String deviceCode,userCode,url; final int interval,expiresIn; DeviceCode(String deviceCode,String userCode,String url,int interval,int expiresIn) { this.deviceCode=deviceCode; this.userCode=userCode; this.url=url; this.interval=Math.max(5,interval); this.expiresIn=expiresIn; } }
    private static final class Token { final String accessToken,refreshToken; Token(String accessToken,String refreshToken) { this.accessToken=accessToken; this.refreshToken=refreshToken; } }
}
