package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.util.EnumMap;
import java.util.Map;
import java.util.Calendar;

/** TV dialogs and local persistence for profiles; no account/login integration. */
final class ProfileController {
    private final MainActivity activity;
    private final SharedPreferences prefs;
    final ViewerProfiles model;
    private final Runnable changed;
    private final GoogleProfileSync googleSync;
    private final ImageView account;
    Dialog dialog;
    private MediaRepository media;
    void setMediaRepository(MediaRepository media) { this.media=media; }
    private final float density;
    private boolean destroyed,onboarding;
    private final android.os.Handler steps=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Bitmap[] pixelAtlases = new Bitmap[2];
    private final Rect[] pixelHeads = new Rect[24];

    ProfileController(MainActivity activity, ImageView account, Runnable changed) {
        this.activity = activity; this.account = account; this.changed = changed;
        density = activity.getResources().getDisplayMetrics().density;
        prefs = activity.getSharedPreferences("viewer_profiles_v1", Activity.MODE_PRIVATE);
        ViewerProfiles loaded;
        boolean recovered = false;
        try { loaded = ViewerProfiles.fromJson(prefs.getString("data", "")); }
        catch (Exception e) {
            // Retain the damaged payload for recovery rather than overwriting it on load.
            prefs.edit().putString("recovery", prefs.getString("data", "")).apply();
            loaded = new ViewerProfiles();
            recovered = true;
            ThemedNotice.show(activity,R.drawable.ic_profile,"Não foi possível carregar os perfis","Uma cópia foi preservada",false,true);
        }
        model = loaded;
        googleSync = new GoogleProfileSync(activity,model,this::persistFromSync);
        if (!recovered) prefs.edit().putString("data", model.toJson()).apply();
        updateAvatar();
    }
    void destroy() { destroyed = true; googleSync.destroy(); steps.removeCallbacksAndMessages(null); if (dialog != null) dialog.dismiss(); for (int i=0;i<pixelAtlases.length;i++) if (pixelAtlases[i]!=null) { pixelAtlases[i].recycle(); pixelAtlases[i]=null; } }
    void savedNotice(String message) { activity.showGlassNotice(R.drawable.ic_check,message,null,false); }
    void syncNow() { googleSync.syncAll(); }
    int dp(int value) { return Math.round(value * density); }
    private void persistFromSync() { prefs.edit().putString("data", model.toJson()).apply(); updateAvatar(); changed.run(); }
    private void persist() { persistFromSync(); googleSync.syncAll(); }
    private void updateAvatar() {
        avatar(account, model.selected());
        account.setContentDescription("Perfil " + model.selected().name + ". Trocar ou editar perfil");
    }
    void avatar(ImageView target, ViewerProfiles.Profile p) {
        int size = 96;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ViewerProfiles.COLORS[p.color]);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        int index = java.util.Arrays.asList(ViewerProfiles.AVATARS).indexOf(p.avatar);
        int atlasIndex = Math.max(0,index) / 12;
        if (pixelAtlases[atlasIndex] == null) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
            pixelAtlases[atlasIndex] = BitmapFactory.decodeResource(activity.getResources(), atlasIndex == 0 ? R.drawable.avatar_animals_pixel : R.drawable.avatar_animals_pixel_extra, options);
        }
        Bitmap pixelAtlas = pixelAtlases[atlasIndex];
        if (pixelAtlas != null && index >= 0) {
            if (pixelHeads[index] == null) {
                // Trim transparent margins once, so all views center the actual head, including ears.
                int[] bounds = PIXEL_HEAD_BOUNDS[index];
                float scaleX = pixelAtlas.getWidth() / 1448f, scaleY = pixelAtlas.getHeight() / 1086f;
                int x0=Math.round(bounds[0]*scaleX),y0=Math.round(bounds[1]*scaleY),width=Math.round(bounds[2]*scaleX)-x0,height=Math.round(bounds[3]*scaleY)-y0;
                int[] pixels=new int[width*height]; pixelAtlas.getPixels(pixels,0,width,x0,y0,width,height);
                int left=pixelAtlas.getWidth(), top=pixelAtlas.getHeight(), right=0, bottom=0;
                for (int y=0;y<height;y++) for (int x=0;x<width;x++)
                    if (Color.alpha(pixels[y*width+x]) > 32) { left=Math.min(left,x0+x); top=Math.min(top,y0+y); right=Math.max(right,x0+x+1); bottom=Math.max(bottom,y0+y+1); }
                pixelHeads[index] = right > left ? new Rect(left,top,right,bottom) : new Rect(0,0,1,1);
            }
            Rect source = pixelHeads[index];
            float factor = Math.min(84f / source.width(), 84f / source.height());
            int w = Math.round(source.width() * factor), h = Math.round(source.height() * factor);
            int left = Math.round((96-w)/2f), top = Math.round((96-h)/2f);
            canvas.drawBitmap(pixelAtlas, source, new Rect(left,top,left+w,top+h), paint);
        }
        target.setImageTintList(null);
        int inset = target == account ? dp(3) : 0;
        target.setPadding(inset,inset,inset,inset);
        target.setScaleType(ImageView.ScaleType.FIT_CENTER);
        target.setImageBitmap(bitmap);
        ((BitmapDrawable) target.getDrawable()).setFilterBitmap(false);
    }
    LinearLayout panel(String title, String subtitle) {
        if (dialog==null||!dialog.isShowing()) { dialog = new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); }
        else { dialog.setOnCancelListener(null); dialog.setOnDismissListener(null); }
        LinearLayout panel = new LinearLayout(activity); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xff242329); bg.setCornerRadius(dp(22)); bg.setStroke(dp(1), 0xff57545d); panel.setBackground(bg);
        TextView heading = text(title, 21); heading.setPadding(dp(10), dp(5), dp(10), dp(8)); panel.addView(heading);
        if (subtitle != null) { TextView hint = text(subtitle, 13); hint.setTextColor(0xffdddddf); hint.setPadding(dp(10), 0, dp(10), dp(12)); panel.addView(hint); }
        dialog.setCancelable(true); dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) { window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setDimAmount(.65f); }
        return panel;
    }
    TextView text(String value, int sp) {
        TextView text = new TextView(activity); text.setText(value); text.setTextColor(Color.WHITE); text.setTextSize(sp); text.setTypeface(activity.getResources().getFont(R.font.outfit)); return text;
    }
    LinearLayout rows(LinearLayout panel) {
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true); scroll.setScrollbarFadingEnabled(false);
        scroll.setScrollBarSize(dp(4)); scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scroll.setScrollIndicators(View.SCROLL_INDICATOR_TOP|View.SCROLL_INDICATOR_BOTTOM);
        LinearLayout rows = new LinearLayout(activity); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows); panel.addView(scroll); return rows;
    }
    void show(LinearLayout panel, View focus, View returnFocus) {
        show(panel,focus,returnFocus,440);
    }
    private void show(LinearLayout panel,View focus,View returnFocus,int widthDp) {
        if (destroyed || activity.isFinishing()) return;
        dialog.setOnDismissListener(d -> { if (returnFocus != null && returnFocus.isShown()) returnFocus.requestFocus(); });
        dialog.show();
        int available = activity.getResources().getDisplayMetrics().heightPixels - dp(72);
        int width = Math.min(dp(widthDp), activity.getResources().getDisplayMetrics().widthPixels - dp(80));
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int height = panel.getMeasuredHeight();
        for (int i = 0; i < panel.getChildCount(); i++) if (panel.getChildAt(i) instanceof ScrollView) {
            ScrollView scroll = (ScrollView) panel.getChildAt(i);
            scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.min(scroll.getMeasuredHeight(), Math.max(dp(100), available - (height - scroll.getMeasuredHeight())))));
        }
        dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (focus != null) focus.requestFocus();
    }
    View row(LinearLayout rows, String title, String subtitle, int icon, Runnable action) {
        View row = activity.getLayoutInflater().inflate(R.layout.item_watch_app, rows, false);
        row.setFocusableInTouchMode(true);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status); status.setText(subtitle); status.setVisibility(subtitle == null ? View.GONE : View.VISIBLE);
        ImageView image = row.findViewById(R.id.icon); image.setDuplicateParentStateEnabled(true);
        if (icon != 0) { image.setImageResource(icon); image.setImageTintList(((TextView) row.findViewById(R.id.title)).getTextColors()); }
        else image.setVisibility(View.GONE);
        row.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
        rows.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
        return row;
    }
    View mediaRow(LinearLayout rows,MediaRepository.MediaItem item,String subtitle,Runnable action) {
        View row=row(rows,item.title,subtitle,item.isSeries()?R.drawable.ic_livetv:R.drawable.ic_video,action);
        TextView detail=row.findViewById(R.id.status); detail.setSingleLine(false); detail.setMaxLines(3);
        TextView original=text("",13); original.setTextColor(activity.getResources().getColorStateList(R.drawable.pill_text,null)); original.setPadding(0,0,0,dp(4)); original.setDuplicateParentStateEnabled(true); original.setSingleLine(true); original.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ((LinearLayout)detail.getParent()).addView(original,1);
        if (media!=null) media.bindTitle(row.findViewById(R.id.title),original,item);
        ImageView cover=row.findViewById(R.id.icon); cover.setImageTintList(null); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setLayoutParams(new LinearLayout.LayoutParams(dp(46),dp(68)));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xff48464e); bg.setCornerRadius(dp(6)); cover.setBackground(bg); cover.setClipToOutline(true);
        if (media!=null) media.loadArtwork(item,true,cover,92,136);
        row.setMinimumHeight(dp(84));
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT); size.setMargins(0,dp(4),0,dp(4)); row.setLayoutParams(size);
        return row;
    }
    private TextView actionButton(LinearLayout panel,String label,int image,Runnable action) {
        TextView button=text(label,16); styleButton(button); button.setGravity(android.view.Gravity.CENTER); button.setFocusableInTouchMode(true); button.setClickable(true);
        if (image!=0) { android.graphics.drawable.Drawable icon=activity.getDrawable(image).mutate(); icon.setTintList(button.getTextColors()); icon.setBounds(0,0,dp(18),dp(18)); button.setCompoundDrawables(icon,null,null,null); }
        button.setCompoundDrawablePadding(dp(8)); button.setPadding(dp(18),0,dp(18),0);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(46)); size.setMargins(dp(4),dp(12),dp(4),dp(4)); panel.addView(button,size);
        button.setOnClickListener(v -> action.run()); return button;
    }
    private TextView backButton(LinearLayout panel,Runnable back) {
        return actionButton(panel,"Voltar",R.drawable.ic_back,() -> { android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE); if (ime!=null) ime.hideSoftInputFromWindow(panel.getWindowToken(),0); back.run(); });
    }
    private ImageView headerBack(LinearLayout header) {
        ImageView back=new ImageView(activity); back.setImageResource(R.drawable.ic_back);
        back.setImageTintList(activity.getResources().getColorStateList(R.drawable.pill_text,activity.getTheme()));
        back.setBackgroundResource(R.drawable.media_next_btn_bg); back.setPadding(dp(8),dp(8),dp(8),dp(8));
        back.setContentDescription("Voltar"); back.setFocusableInTouchMode(true); back.setClickable(true); back.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(36),dp(36)); size.setMarginEnd(dp(12)); header.addView(back,0,size);
        return back;
    }
    private void profileActions(LinearLayout panel,Runnable back,Runnable save) {
        LinearLayout actions=new LinearLayout(activity); actions.setBaselineAligned(false); panel.addView(actions);
        TextView left=backButton(actions,back),right=actionButton(actions,"Salvar",0,save);
        for (TextView button:new TextView[]{left,right}) { LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(46),1); size.setMargins(dp(4),dp(12),dp(4),dp(4)); button.setLayoutParams(size); }
    }
    private void backOnCancel(Runnable back) { dialog.setOnCancelListener(d -> steps.post(() -> { if (!destroyed) back.run(); })); }
    void ensureFirstProfile() {
        if (!destroyed&&model.needsIndividualProfile()&&(dialog==null||!dialog.isShowing())) { onboarding=true; createProfile(); }
    }
    void showProfiles() {
        if (model.needsIndividualProfile()) { ensureFirstProfile(); return; }
        LinearLayout panel = panel("Quem vai assistir?", "Perfil atual: " + model.selected().name);
        View heading=panel.getChildAt(0),subtitle=panel.getChildAt(1); panel.removeView(heading); panel.removeView(subtitle);
        heading.setPadding(0,0,0,dp(4)); subtitle.setPadding(0,0,0,0);
        LinearLayout words=new LinearLayout(activity); words.setOrientation(LinearLayout.VERTICAL); words.addView(heading); words.addView(subtitle);
        LinearLayout header=new LinearLayout(activity); header.setGravity(android.view.Gravity.CENTER_VERTICAL); header.setPadding(dp(8),dp(4),dp(8),dp(14));
        header.addView(words,new LinearLayout.LayoutParams(0,-2,1)); panel.addView(header,0); ImageView back=headerBack(header);
        LinearLayout rows = rows(panel); View focus = null;
        for (ViewerProfiles.Profile p : model.profiles()) {
            View row = row(rows, p.name, p.aggregate() ? "Combina os gostos de todos" : p.votes.size() + (p.votes.size() == 1 ? " título registrado" : " títulos registrados"), 0, () -> { model.select(p.id); persist(); });
            ImageView image = row.findViewById(R.id.icon); image.setVisibility(View.VISIBLE); avatar(image, p);
            if (p.id.equals(model.selected().id)) focus = row;
        }
        row(rows, "Criar perfil", "Nome, avatar e cor", R.drawable.ic_profile, this::createProfile);
        row(rows, "Editar perfis", "Alterar nome e foto", R.drawable.ic_settings, this::chooseEdit);
        row(rows, "Contas Google e sincronização", "Vincular cada perfil e importar dados", R.drawable.ic_profile, this::showSyncProfiles);
        View selected=focus;
        back.setOnKeyListener((v,key,event) -> { if (event.getAction()==android.view.KeyEvent.ACTION_DOWN&&(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN||key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)&&selected!=null) { selected.requestFocus(); return true; } return false; });
        show(panel, focus, account);
    }

    private void showSyncProfiles() {
        LinearLayout panel=panel("Contas Google","Cada perfil pode usar uma conta diferente. Alterações locais continuam funcionando sem internet.");
        LinearLayout rows=rows(panel); View focus=null;
        for (ViewerProfiles.Profile p:model.profiles()) if (!p.aggregate()) {
            View row=row(rows,p.name,googleSync.status(p.id),R.drawable.ic_profile,() -> showSyncProfile(p.id));
            if (focus==null) focus=row;
        }
        if (rows.getChildCount()==0) row(rows,"Crie um perfil individual primeiro","O Geral não recebe uma conta Google",R.drawable.ic_profile,() -> {});
        row(rows,"Sincronizar todos agora","Atualizar todos os perfis já vinculados",R.drawable.ic_check,() -> { dialog.dismiss(); googleSync.syncAll(); savedNotice("Sincronização iniciada"); });
        show(panel,focus==null?rows.getChildAt(0):focus,account);
    }

    private void showSyncProfile(String id) {
        ViewerProfiles.Profile profile=model.find(id); if (profile==null||profile.aggregate()) return;
        LinearLayout panel=panel("Conta de "+profile.name,"Os dados deste perfil ficam separados dos demais."); LinearLayout rows=rows(panel); View focus=null;
        if (googleSync.linked(id)) {
            focus=row(rows,"Sincronizar agora",googleSync.status(id),R.drawable.ic_check,() -> { dialog.dismiss(); googleSync.syncAll(); savedNotice("Sincronização iniciada"); });
            row(rows,"Desvincular conta","Manter os dados locais e parar a sincronização",R.drawable.ic_close_x,() -> { googleSync.unlink(id); dialog.dismiss(); savedNotice("Conta desvinculada"); });
        } else {
            focus=row(rows,"Vincular conta Google","Escanear QR Code para autorizar pelo celular",R.drawable.ic_profile,() -> linkGoogle(id));
        }
        row(rows,"Voltar","Escolher outro perfil",R.drawable.ic_back, this::showSyncProfiles);
        show(panel,focus,account);
    }

    private void linkGoogle(String id) {
        final GoogleProfileSync.LinkHandle[] handle={null};
        LinearLayout waiting=panel("Vinculando conta Google","Preparando o código de autorização…"); LinearLayout rows=rows(waiting);
        TextView status=text("Aguarde…",15); status.setTextColor(0xffbcbcc7); rows.addView(status);
        View cancel=row(rows,"Cancelar","Não altera os dados deste perfil",R.drawable.ic_close_x,() -> { if (handle[0]!=null) handle[0].cancel(); dialog.dismiss(); });
        show(waiting,status,account);
        handle[0]=googleSync.link(id,new GoogleProfileSync.LinkListener() {
            @Override public void code(String url,String userCode) { showGoogleCode(id,url,userCode,handle[0]); }
            @Override public void status(String message) { status.setText(message); }
            @Override public void success(String email) { if (dialog!=null) dialog.dismiss(); savedNotice("Conta vinculada"+(email.isEmpty()?"":" · "+email)); }
            @Override public void error(String message) { if (dialog!=null) dialog.dismiss(); savedNotice(message); }
        });
    }

    private void showGoogleCode(String id,String url,String userCode,GoogleProfileSync.LinkHandle handle) {
        ViewerProfiles.Profile profile=model.find(id); if (profile==null) return;
        LinearLayout panel=panel("Autorizar "+profile.name,"Escaneie o QR Code com o celular. Ele abrirá a página oficial do Google."); LinearLayout rows=rows(panel);
        ImageView qr=new ImageView(activity); qr.setContentDescription("QR Code para autorizar a conta Google de "+profile.name); qr.setScaleType(ImageView.ScaleType.CENTER_INSIDE); qr.setImageBitmap(qrBitmap(url));
        LinearLayout.LayoutParams qrSize=new LinearLayout.LayoutParams(dp(220),dp(220)); qrSize.gravity=android.view.Gravity.CENTER_HORIZONTAL; rows.addView(qr,qrSize);
        TextView address=text(url.replace("https://",""),13); address.setTextColor(0xffbcbcc7); address.setGravity(android.view.Gravity.CENTER); address.setPadding(dp(10),dp(4),dp(10),dp(4)); rows.addView(address);
        TextView code=text("Código alternativo: "+userCode,18); code.setGravity(android.view.Gravity.CENTER); code.setLetterSpacing(.06f); code.setPadding(0,dp(8),0,dp(12)); rows.addView(code);
        TextView waiting=text("Aguardando aprovação…",14); waiting.setTextColor(0xffbcbcc7); waiting.setGravity(android.view.Gravity.CENTER); rows.addView(waiting);
        row(rows,"Cancelar","A autorização continuará sem alterar o perfil",R.drawable.ic_close_x,() -> { handle.cancel(); dialog.dismiss(); });
        show(panel,qr,account);
    }
    private Bitmap qrBitmap(String value) {
        int size=dp(220); BitMatrix matrix;
        try {
            Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class); hints.put(EncodeHintType.MARGIN,1);
            matrix=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,size,size,hints);
        } catch (Exception e) { return Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888); }
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        for (int y=0;y<size;y++) for (int x=0;x<size;x++) bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
        return bitmap;
    }
    void chooseWatchLater(MediaRepository.MediaItem item,View anchor) {
        java.util.Set<String> people=new java.util.LinkedHashSet<>(java.util.Collections.singleton(model.selected().id));
        LinearLayout panel=panel("Assistir mais tarde",item.displayTitles()+"\nEscolha os perfis"); LinearLayout options=rows(panel); View focus=null;
        for (ViewerProfiles.Profile p:model.profiles()) {
            String status=p.aggregate()?"Lista conjunta":p.watchLater.containsKey(item.imdbId)?"Já está na lista":"Lista de "+p.name;
            View option=row(options,p.name,status,0,() -> {}); option.setTag(p.id); checkPersonRow(option,p.name,status,people.contains(p.id));
            ImageView image=option.findViewById(R.id.icon); image.setVisibility(View.VISIBLE); avatar(image,p);
            if (p.id.equals(model.selected().id)) focus=option;
            option.setOnClickListener(v -> { ViewerProfiles.togglePerson(people,p.id); for (int i=0;i<options.getChildCount();i++) { View child=options.getChildAt(i); checkPersonRow(child,model.find((String)child.getTag()).name,((TextView)child.findViewById(R.id.status)).getText().toString(),people.contains(child.getTag())); } });
        }
        TextView error=text("",13); error.setTextColor(0xffffb7b7); panel.addView(error);
        profileActions(panel,() -> dialog.dismiss(),() -> { try { model.saveForLater(people,item); persist(); dialog.dismiss(); savedNotice("Salvo para assistir mais tarde"); } catch (IllegalArgumentException e) { error.setText(e.getMessage()); } });
        show(panel,focus==null?options.getChildAt(0):focus,anchor);
    }
    void removeSavedItem(MediaRepository.MediaItem item) {
        model.removeWatchLater(model.selected().id,item.imdbId); persist(); savedNotice("Removido de Assistir mais tarde");
    }
    private void chooseEdit() {
        LinearLayout panel = panel("Editar perfis", "Escolha o perfil que deseja editar"); LinearLayout rows = rows(panel);
        for (ViewerProfiles.Profile p : model.profiles()) {
            View row = row(rows, p.name, p.aggregate() ? "Perfil combinado" : "Perfil individual", 0, () -> edit(p.id));
            ImageView image = row.findViewById(R.id.icon); image.setVisibility(View.VISIBLE); avatar(image, p);
        }
        backButton(panel,this::showProfiles);
        show(panel, rows.getChildAt(0), account); backOnCancel(this::showProfiles);
    }
    private void edit(String id) {
        ViewerProfiles.Profile p = model.find(id); if (p == null) return;
        LinearLayout panel = panel(p.name, "Editar perfil"); LinearLayout rows = rows(panel);
        row(rows, "Alterar nome", p.name, R.drawable.ic_profile, () -> editName(id));
        row(rows, "Avatar e cor", "Escolher o animal e a cor de fundo", R.drawable.ic_profile, () -> showAppearance(id));
        backButton(panel,this::chooseEdit);
        show(panel, rows.getChildAt(0), account); backOnCancel(this::chooseEdit);
    }
    private void editName(String id) {
        ViewerProfiles.Profile p = model.find(id);
        if (p==null) return;
        LinearLayout panel = panel("Alterar nome", "Até 32 caracteres");
        EditText input = new EditText(activity); input.setSingleLine(true); input.setTextColor(Color.WHITE); input.setHintTextColor(0xffaaaaaa); input.setHint("Nome do perfil");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
        input.setText(p.name); input.setSelection(input.length()); panel.addView(input);
        input.setBackgroundResource(R.drawable.discovery_choice_bg); input.setTypeface(activity.getResources().getFont(R.font.outfit)); input.setPadding(dp(16),dp(12),dp(16),dp(12));
        TextView error = text("", 13); error.setTextColor(0xffffb7b7); panel.addView(error);
        Runnable back=() -> edit(id);
        profileActions(panel,back,() -> {
            try { ViewerProfiles.Profile saved = model.saveName(id, input.getText().toString()); persist(); dialog.dismiss(); edit(saved.id); }
            catch (IllegalArgumentException e) { error.setText(e.getMessage()); input.requestFocus(); }
        });
        show(panel, input, account); backOnCancel(back);
    }
    private void createProfile() {
        String animal=ViewerProfiles.AVATARS[model.profiles().size()%ViewerProfiles.AVATARS.length];
        for (String candidate:ViewerProfiles.AVATARS) { boolean used=false; for (ViewerProfiles.Profile person:model.profiles()) if (person.avatar.equals(candidate)) used=true; if (!used) { animal=candidate; break; } }
        showCreateProfile(new ViewerProfiles.Profile("draft","",animal));
    }
    private void showCreateProfile(ViewerProfiles.Profile draft) {
        LinearLayout panel=panel(onboarding?"Vamos criar seu perfil":"Criar perfil",onboarding?"O Geral reúne os gostos de todos. Crie seu primeiro perfil individual para começar.":"Escolha o nome, o avatar e a cor do novo perfil.");
        EditText input=new EditText(activity); input.setSingleLine(true); input.setTextColor(Color.WHITE); input.setHintTextColor(0xffaaaaaa); input.setHint("Nome do perfil"); input.setText(draft.name);
        input.setTypeface(activity.getResources().getFont(R.font.outfit)); input.setTextSize(17); input.setBackgroundResource(R.drawable.discovery_choice_bg); input.setPadding(dp(16),dp(12),dp(16),dp(12));
        input.setSelection(input.length());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS); input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)}); panel.addView(input);
        LinearLayout choices=new LinearLayout(activity); choices.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,-2); gap.setMargins(0,dp(12),0,0); panel.addView(choices,gap);
        View appearance=row(choices,"Avatar e cor","Escolher animal e fundo",0,() -> {}); ImageView preview=appearance.findViewById(R.id.icon); preview.setVisibility(View.VISIBLE); avatar(preview,draft); preview.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44))); appearance.setMinimumHeight(dp(64));
        appearance.setOnClickListener(v -> {
            draft.name=input.getText().toString();
            showAppearance(draft,"Usar avatar",(animal,color) -> { draft.avatar=animal; draft.color=color; showCreateProfile(draft); },() -> showCreateProfile(draft));
        });
        TextView error=text("",13); error.setTextColor(0xffffb7b7); panel.addView(error);
        profileActions(panel,this::showProfiles,() -> {
            try {
                model.createProfile(input.getText().toString(),draft.avatar,draft.color); persist();
                if (onboarding) { onboarding=false; dialog.dismiss(); } else showProfiles();
                savedNotice("Perfil criado");
            }
            catch (IllegalArgumentException e) { error.setText(e.getMessage()); input.requestFocus(); }
        });
        show(panel,input,account);
        if (onboarding) {
            ((LinearLayout)panel.getChildAt(panel.getChildCount()-1)).getChildAt(0).setVisibility(View.GONE);
            dialog.setCancelable(false);
        } else backOnCancel(this::showProfiles);
    }
    void chooseRater(MediaRepository.MediaItem item, View anchor) {
        chooseRater(item,anchor,null);
    }
    void chooseRater(MediaRepository.MediaItem item,View anchor,java.util.function.Consumer<Boolean> done) {
        chooseRaters(item,anchor,new java.util.LinkedHashSet<>(java.util.Collections.singleton(model.selected().id)),done);
    }
    private void chooseRaters(MediaRepository.MediaItem item,View anchor,java.util.Set<String> people,java.util.function.Consumer<Boolean> done) {
        LinearLayout panel = panel("Quem assistiu?", item.displayTitles()+"\nCada pessoa dará sua própria nota"); LinearLayout rows = rows(panel); View focus = null;
        for (ViewerProfiles.Profile p : model.profiles()) {
            ViewerProfiles.Vote vote = model.vote(p.id, item.imdbId);
            String status = vote == null ? p.aggregate() ? "Avaliação conjunta, sem alterar os individuais" : "Ainda não avaliou" : vote.rating < 0 ? "Assistido · sem avaliação" : "Já avaliou: "+ViewerProfiles.RATINGS[vote.rating];
            View row = row(rows, p.name, status, 0, () -> {}); row.setTag(p.id);
            ((LinearLayout.LayoutParams)row.getLayoutParams()).setMargins(0,dp(4),0,dp(4));
            checkPersonRow(row,p.name,status,people.contains(p.id));
            row.setOnClickListener(v -> {
                ViewerProfiles.togglePerson(people,p.id);
                for (int i=0;i<rows.getChildCount();i++) { View option=rows.getChildAt(i); ViewerProfiles.Profile person=model.find((String)option.getTag());
                    checkPersonRow(option,person.name,((TextView)option.findViewById(R.id.status)).getText().toString(),people.contains(person.id)); }
            });
            ImageView image = row.findViewById(R.id.icon); image.setVisibility(View.VISIBLE); avatar(image, p);
            if (p.id.equals(model.selected().id)) focus = row;
        }
        TextView error=text("",13); error.setTextColor(0xffffb7b7); panel.addView(error);
        TextView next=text("Continuar",17); next.setGravity(android.view.Gravity.CENTER); styleButton(next); next.setFocusableInTouchMode(true); next.setClickable(true);
        panel.addView(next,new LinearLayout.LayoutParams(-1,dp(50)));
        next.setOnClickListener(v -> { if (people.isEmpty()) error.setText("Selecione pelo menos uma pessoa."); else confirmRating(new java.util.LinkedHashSet<>(people),item,anchor,done); });
        show(panel, focus == null ? rows.getChildAt(0) : focus, anchor);
    }
    String priorRatings(MediaRepository.MediaItem item) {
        java.util.List<String> notes=new java.util.ArrayList<>();
        for (ViewerProfiles.Profile person:model.profiles()) { ViewerProfiles.Vote vote=model.vote(person.id,item.imdbId); if (vote!=null&&vote.rating>=0) notes.add(person.name+": "+ViewerProfiles.RATINGS[vote.rating]); }
        return notes.isEmpty()?"":"Já avaliado · "+String.join(" · ",notes);
    }
    private void confirmRating(java.util.Set<String> people,MediaRepository.MediaItem item,View anchor,java.util.function.Consumer<Boolean> done) {
        java.util.List<String> existing=new java.util.ArrayList<>(); java.util.Set<String> fresh=new java.util.LinkedHashSet<>();
        for (String id:people) { ViewerProfiles.Vote vote=model.vote(id,item.imdbId); if (vote!=null&&vote.rating>=0) existing.add(model.find(id).name+": "+ViewerProfiles.RATINGS[vote.rating]); else fresh.add(id); }
        if (existing.isEmpty()) { rate(people,item,anchor,done); return; }
        LinearLayout panel=panel("Este título já foi avaliado",item.displayTitles()+"\n"+String.join(" · ",existing)); LinearLayout rows=rows(panel);
        View keep=row(rows,"Manter avaliações","Continuar sem alterar as notas",R.drawable.ic_check,() -> {});
        keep.setOnClickListener(v -> { if (done!=null) done.accept(false); else dialog.dismiss(); });
        if (!fresh.isEmpty()) { View option=row(rows,"Avaliar só os perfis sem nota","Preservar as avaliações existentes",R.drawable.ic_profile,() -> {}); option.setOnClickListener(v -> rate(fresh,item,anchor,done)); }
        View replace=row(rows,"Substituir avaliação","Alterar a última nota dos perfis selecionados",R.drawable.ic_edit,() -> {}); replace.setOnClickListener(v -> rate(people,item,anchor,done));
        show(panel,rows.getChildAt(0),anchor);
        dialog.setOnCancelListener(d -> steps.post(() -> { if (!destroyed) chooseRaters(item,anchor,people,done); }));
    }
    void checkPersonRow(View row,String label,String subtitle,boolean selected) {
        row.setSelected(selected); row.setBackgroundResource(R.drawable.discovery_choice_bg);
        TextView title=row.findViewById(R.id.title); title.setText(label+(selected?"  ✓":""));
        title.setTextColor(activity.getResources().getColorStateList(R.color.discovery_choice_text,activity.getTheme()));
        ((TextView)row.findViewById(R.id.status)).setTextColor(activity.getResources().getColorStateList(R.color.discovery_choice_text_dim,activity.getTheme()));
        row.setContentDescription(label+(selected?", selecionado":"")+(subtitle==null?"":", "+subtitle));
    }
    private void rate(String id, MediaRepository.MediaItem item, View anchor) {
        rate(new java.util.LinkedHashSet<>(java.util.Collections.singleton(id)),item,anchor,null);
    }
    private void rate(java.util.Set<String> people,MediaRepository.MediaItem item,View anchor,java.util.function.Consumer<Boolean> done) {
        rateNext(new java.util.ArrayList<>(people),0,new java.util.LinkedHashMap<>(),item,anchor,done);
    }
    private void rateNext(java.util.List<String> people,int index,java.util.Map<String,Integer> draft,MediaRepository.MediaItem item,View anchor,java.util.function.Consumer<Boolean> done) {
        String id=people.get(index); ViewerProfiles.Profile person=model.find(id);
        LinearLayout panel=panel(person.aggregate()?"O que vocês acharam?":"O que "+person.name+" achou?",item.displayTitles()+"\n"+person.name+" · "+(index+1)+" de "+people.size()+" perfis");
        LinearLayout rows=rows(panel);
        int[] icons={R.drawable.ic_thumb_hate,R.drawable.ic_thumb_down,R.drawable.ic_thumb_neutral,R.drawable.ic_thumb_up,R.drawable.ic_thumb_love};
        ViewerProfiles.Vote old=model.vote(id,item.imdbId); Integer chosen=draft.get(id); View focus=null;
        java.util.function.IntConsumer select=rating -> {
            draft.put(id,rating);
            if (index+1<people.size()) rateNext(people,index+1,draft,item,anchor,done);
            else { model.rateIndividually(draft,item); persist(); if (done!=null) done.accept(true); else { dialog.dismiss(); savedNotice(people.size()==1?"Avaliação salva para "+person.name:"Avaliações individuais salvas"); } }
        };
        for (int i=0;i<ViewerProfiles.RATINGS.length;i++) {
            final int rating=i; View option=row(rows,ViewerProfiles.RATINGS[i],old!=null&&old.rating==i?"Avaliação atual de "+person.name:null,icons[i],() -> {});
            option.setOnClickListener(v -> select.accept(rating)); option.findViewById(R.id.icon).setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(32)));
            if (chosen!=null?chosen==i:old!=null&&old.rating==i) focus=option;
        }
        View watched=row(rows,"Só marcar como assistido","Preservar a nota anterior de "+person.name,R.drawable.ic_check,() -> {}); watched.setOnClickListener(v -> select.accept(-1));
        if (old!=null&&people.size()==1) row(rows,"Remover registro","Apagar nota e assistido; voltar a recomendar",R.drawable.ic_close_x,() -> { model.removeVote(id,item.imdbId); persist(); });
        show(panel,focus==null?rows.getChildAt(3):focus,anchor);
        dialog.setOnCancelListener(d -> steps.post(() -> { if (!destroyed) { if (index>0) rateNext(people,index-1,draft,item,anchor,done); else chooseRaters(item,anchor,new java.util.LinkedHashSet<>(people),done); } }));
    }
    private LinearLayout profilePanel(String title,String subtitle) {
        LinearLayout panel=panel(title,null); TextView heading=(TextView)panel.getChildAt(0); panel.removeView(heading);
        LinearLayout header=new LinearLayout(activity); header.setGravity(android.view.Gravity.CENTER_VERTICAL); header.setPadding(dp(8),dp(4),dp(8),dp(14));
        ImageView image=new ImageView(activity); avatar(image,model.selected()); LinearLayout.LayoutParams imageSize=new LinearLayout.LayoutParams(dp(44),dp(44)); imageSize.setMargins(0,0,dp(12),0); header.addView(image,imageSize);
        LinearLayout words=new LinearLayout(activity); words.setOrientation(LinearLayout.VERTICAL); header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        heading.setPadding(0,0,0,dp(4)); words.addView(heading);
        TextView hint=text(subtitle,12); hint.setTextColor(0xffbcbcc7); words.addView(hint); panel.addView(header,0); return panel;
    }
    private LinearLayout tasteCard(LinearLayout parent,String title,String subtitle) {
        LinearLayout card=new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(12),dp(14),dp(8));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xff1c1c23); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1),0xff383840); card.setBackground(bg);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2); size.setMargins(dp(4),0,dp(4),dp(12)); parent.addView(card,size);
        if (title!=null) card.addView(text(title,16));
        if (subtitle!=null) { TextView hint=text(subtitle,12); hint.setTextColor(0xffbcbcc7); hint.setPadding(0,dp(4),0,dp(12)); card.addView(hint); }
        return card;
    }
    private void tasteMetric(LinearLayout parent,String value,String label,String hint) {
        LinearLayout card=tasteCard(parent,null,null); card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(82),1); size.setMargins(dp(4),0,dp(4),dp(12)); card.setLayoutParams(size);
        TextView number=text(value,27); card.addView(number); card.addView(text(label,13));
        TextView detail=text(hint,11); detail.setTextColor(0xffbcbcc7); card.addView(detail);
    }
    void showTaste(View returnFocus) {
        ViewerProfiles.Profile selected=model.selected(); ViewerProfiles.TasteOverview taste=model.overview(selected.id,Calendar.getInstance().get(Calendar.YEAR));
        LinearLayout panel=profilePanel("Gostos de "+selected.name,selected.aggregate()?"Afinidades cruzadas de "+taste.people+" perfis com avaliações":"Seu perfil de cinema, construído a cada avaliação");
        LinearLayout content=rows(panel); content.setPadding(dp(2),0,dp(2),0);
        LinearLayout metrics=new LinearLayout(activity); content.addView(metrics);
        tasteMetric(metrics,String.valueOf(taste.watched),"Títulos assistidos",taste.movies+" filmes · "+taste.series+" séries");
        tasteMetric(metrics,String.valueOf(taste.rated),"Avaliações",selected.aggregate()?"Todos os perfis":"Suas notas");
        tasteMetric(metrics,taste.rated==0?"—":Math.round(taste.liked*100f/taste.rated)+"%","Aprovação","Gostei ou amei");
        if (taste.rated<5) { TextView hint=text(taste.rated==0?"Avalie filmes e séries para começar a revelar suas afinidades.":"Gostos em formação · continue avaliando para refinar suas recomendações.",12); hint.setTextColor(0xffbcbcc7); hint.setPadding(dp(10),0,dp(10),dp(12)); content.addView(hint); }
        LinearLayout charts=new LinearLayout(activity); content.addView(charts);
        LinearLayout genres=tasteCard(charts,"Radar de gêneros",null);
        LinearLayout.LayoutParams genreSize=new LinearLayout.LayoutParams(0,-1,1.45f); genreSize.setMargins(dp(4),0,dp(4),dp(12)); genres.setLayoutParams(genreSize);
        genres.addView(new TasteChartView(activity,taste.category("g:"),true),new LinearLayout.LayoutParams(-1,-2,1));
        LinearLayout ratings=tasteCard(charts,"Suas avaliações","De Odiei a Amei"); LinearLayout.LayoutParams ratingSize=new LinearLayout.LayoutParams(0,-1,1); ratingSize.setMargins(dp(4),0,dp(4),dp(12)); ratings.setLayoutParams(ratingSize);
        ratings.addView(new TasteChartView(activity,taste.ratings),new LinearLayout.LayoutParams(-1,-2,1));
        LinearLayout pair=new LinearLayout(activity); content.addView(pair);
        LinearLayout formats=tasteCard(pair,"Filmes ou séries?","Afinidade por formato");
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-1,1); half.setMargins(dp(4),0,dp(4),dp(12)); formats.setLayoutParams(half);
        java.util.Map<String,Double> types=new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String,Double> entry:taste.category("t:").entrySet()) types.put(entry.getKey().equals("series")?"Séries":"Filmes",entry.getValue());
        formats.addView(new TasteChartView(activity,types),new LinearLayout.LayoutParams(-1,-2,1));
        LinearLayout eras=tasteCard(pair,"Sua época","Afinidade por lançamento"); LinearLayout.LayoutParams other=new LinearLayout.LayoutParams(0,-1,1); other.setMargins(dp(4),0,dp(4),dp(12)); eras.setLayoutParams(other);
        java.util.Map<String,Double> periods=new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String,Double> entry:taste.category("e:").entrySet()) periods.put(entry.getKey().startsWith("Recentes")?"Recentes":entry.getKey().startsWith("Clássicos")?"Clássicos":"De 6 a 15 anos",entry.getValue());
        eras.addView(new TasteChartView(activity,periods),new LinearLayout.LayoutParams(-1,-2,1));
        LinearLayout decades=tasteCard(pair,"Décadas em cena","Afinidade ao longo do tempo"); LinearLayout.LayoutParams third=new LinearLayout.LayoutParams(0,-1,1); third.setMargins(dp(4),0,dp(4),dp(12)); decades.setLayoutParams(third); decades.addView(new TasteChartView(activity,taste.category("d:")),new LinearLayout.LayoutParams(-1,-2,1));
        TextView note=text(selected.aggregate()?"O Geral combina os gostos e dá mais peso às afinidades em comum.":"Cada avaliação confirmada atualiza este painel e suas recomendações.",12); note.setTextColor(0xffbcbcc7); note.setPadding(dp(10),0,dp(10),dp(8)); content.addView(note);
        TextView scale=text("Afinidade de 0 a 100% · 50% indica neutralidade.",11); scale.setTextColor(0xffbcbcc7); scale.setPadding(dp(10),0,dp(10),dp(8)); content.addView(scale);
        ImageView back=headerBack((LinearLayout)panel.getChildAt(0));
        ScrollView scroll=(ScrollView)content.getParent(); scroll.setFocusableInTouchMode(true);
        scroll.setOnKeyListener((v,key,event) -> {
            if (event.getAction()!=android.view.KeyEvent.ACTION_DOWN) return false;
            if (key==android.view.KeyEvent.KEYCODE_DPAD_DOWN) { if (scroll.canScrollVertically(1)) scroll.smoothScrollBy(0,dp(140)); return true; }
            if (key==android.view.KeyEvent.KEYCODE_DPAD_UP) { if (scroll.canScrollVertically(-1)) scroll.smoothScrollBy(0,-dp(140)); else back.requestFocus(); return true; }
            return false;
        });
        back.setOnKeyListener((v,key,event) -> { if (event.getAction()==android.view.KeyEvent.ACTION_DOWN&&(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN||key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)) { scroll.requestFocus(); return true; } return false; });
        show(panel,scroll,returnFocus,700);
    }
    private static final int[][] PIXEL_HEAD_BOUNDS = {
        {50,60,350,360}, {380,80,710,360}, {740,45,1070,365}, {1110,80,1420,360},
        {20,410,390,700}, {395,390,710,700}, {750,390,1070,700}, {1100,370,1435,715},
        {30,730,380,1045}, {410,690,700,1050}, {730,750,1085,1045}, {1095,705,1440,1070},
        {0,65,380,375}, {380,70,715,375}, {745,95,1070,375}, {1100,75,1435,380},
        {20,430,360,695}, {390,405,710,695}, {740,395,1070,700}, {1080,390,1448,700},
        {35,695,335,1060}, {370,705,730,1050}, {745,710,1060,1055}, {1100,735,1435,1055}
    };
    void styleButton(TextView button) {
        button.setBackgroundResource(R.drawable.pill_bg);
        button.setTextColor(activity.getResources().getColorStateList(R.color.modal_button_text,activity.getTheme()));
    }
    private void showAppearance(String id) {
        ViewerProfiles.Profile p = model.find(id); if (p == null) return;
        showAppearance(p,"Salvar",(animal,color) -> { model.setAppearance(id,animal,color); persist(); edit(id); },() -> edit(id));
    }
    private void showAppearance(ViewerProfiles.Profile p,String confirm,java.util.function.BiConsumer<String,Integer> saveAppearance,Runnable backAction) {
        String[] animals = {"Gato", "Cachorro", "Raposa", "Panda", "Coala", "Tigre", "Urso", "Lobo", "Macaco", "Coelho", "Porquinho", "Leão",
                "Elefante", "Guaxinim", "Lontra", "Coruja", "Sapo", "Pinguim", "Pato", "Vaca", "Cavalo", "Girafa", "Zebra", "Capivara"};
        String[] colors = {"Azul", "Roxo", "Turquesa", "Rosa", "Laranja", "Verde", "Grafite", "Vermelho",
                "Azul-marinho", "Violeta", "Verde-lima", "Mostarda", "Terracota", "Azul-claro", "Malva", "Café",
                "Menta", "Cobalto", "Lilás", "Areia", "Salmão", "Oliva", "Ameixa", "Carvão"};
        String[] draftAvatar = {p.avatar}; int[] draftColor = {p.color};
        LinearLayout panel = panel("Avatar e cor", (p.name.isEmpty()?"Novo perfil":p.name) + " · escolha o animal e a cor");
        LinearLayout content = rows(panel);
        java.util.List<View> cells = new java.util.ArrayList<>();
        java.util.List<View> colorCells = new java.util.ArrayList<>();
        java.util.List<ImageView> previews = new java.util.ArrayList<>();
        java.util.List<TextView> labels = new java.util.ArrayList<>();
        java.util.List<TextView> checks = new java.util.ArrayList<>();
        View[] saveButton = new View[1];
        android.content.res.ColorStateList textColors = new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused},new int[]{}}, new int[]{Color.BLACK,Color.WHITE});
        LinearLayout line = null;
        for (int i = 0; i < animals.length; i++) {
            final int index = i;
            if (i % 4 == 0) { line = new LinearLayout(activity); line.setOrientation(LinearLayout.HORIZONTAL); content.addView(line); }
            LinearLayout cell = new LinearLayout(activity); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(android.view.Gravity.CENTER);
            cell.setBackgroundResource(R.drawable.input_row_bg); cell.setFocusableInTouchMode(true); cell.setClickable(true);
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0, dp(78), 1); layout.setMargins(dp(2), dp(2), dp(2), dp(2)); line.addView(cell, layout);
            ImageView image = new ImageView(activity); cell.addView(image, new LinearLayout.LayoutParams(dp(48), dp(48))); previews.add(image);
            ViewerProfiles.Profile preview = new ViewerProfiles.Profile(p.id, p.name, ViewerProfiles.AVATARS[i]); preview.color = draftColor[0]; avatar(image, preview);
            TextView label = text(animals[i] + (p.avatar.equals(ViewerProfiles.AVATARS[i]) ? " ✓" : ""), 12);
            label.setGravity(android.view.Gravity.CENTER); label.setSingleLine(true); label.setTextColor(textColors); label.setDuplicateParentStateEnabled(true); cell.addView(label); labels.add(label);
            cell.setContentDescription(animals[i] + (p.avatar.equals(ViewerProfiles.AVATARS[i]) ? ", selecionado" : ""));
            cell.setOnClickListener(v -> {
                draftAvatar[0] = ViewerProfiles.AVATARS[index];
                for (int j = 0; j < animals.length; j++) {
                    boolean selected = j == index; labels.get(j).setText(animals[j] + (selected ? " ✓" : "")); cells.get(j).setContentDescription(animals[j] + (selected ? ", selecionado" : ""));
                }
            });
            cells.add(cell);
            cell.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                int target = index;
                if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT) { if (index % 4 > 0) target--; }
                else if (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) { if (index % 4 < 3) target++; }
                else if (key == android.view.KeyEvent.KEYCODE_DPAD_UP) { if (index >= 4) target -= 4; }
                else if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (index + 4 < animals.length) target += 4;
                    else { colorCells.get(index % 4 * 2).requestFocus(); return true; }
                } else return false;
                cells.get(target).requestFocus(); return true;
            });
        }
        TextView colorTitle = text("Cor de fundo · " + colors[p.color], 13); colorTitle.setPadding(dp(10), dp(5), dp(10), dp(2)); panel.addView(colorTitle);
        android.widget.HorizontalScrollView colorScroll = new android.widget.HorizontalScrollView(activity);
        colorScroll.setHorizontalScrollBarEnabled(true); colorScroll.setScrollbarFadingEnabled(false); colorScroll.setFocusable(false);
        LinearLayout palette = new LinearLayout(activity); palette.setOrientation(LinearLayout.HORIZONTAL); colorScroll.addView(palette);
        panel.addView(colorScroll,new LinearLayout.LayoutParams(-1,dp(54)));
        for (int i = 0; i < colors.length; i++) {
            final int index = i;
            LinearLayout swatch = new LinearLayout(activity); swatch.setGravity(android.view.Gravity.CENTER); swatch.setBackgroundResource(R.drawable.input_row_bg);
            swatch.setFocusableInTouchMode(true); swatch.setClickable(true); palette.addView(swatch, new LinearLayout.LayoutParams(dp(48), dp(48))); colorCells.add(swatch);
            TextView check = text(i == draftColor[0] ? "✓" : "", 17); check.setGravity(android.view.Gravity.CENTER);
            check.setTextColor(androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE,ViewerProfiles.COLORS[i])>=4.5?Color.WHITE:Color.BLACK);
            GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL); circle.setColor(ViewerProfiles.COLORS[i]); circle.setStroke(dp(1),0x80ffffff); check.setBackground(circle);
            swatch.addView(check, new LinearLayout.LayoutParams(dp(30),dp(30))); checks.add(check); swatch.setContentDescription(colors[i] + (i == draftColor[0] ? ", selecionado" : ""));
            swatch.setOnClickListener(v -> {
                draftColor[0] = index; colorTitle.setText("Cor de fundo · " + colors[index]);
                for (int j = 0; j < colors.length; j++) { checks.get(j).setText(j == index ? "✓" : ""); colorCells.get(j).setContentDescription(colors[j] + (j == index ? ", selecionado" : "")); }
                for (int j = 0; j < animals.length; j++) { ViewerProfiles.Profile preview = new ViewerProfiles.Profile(p.id,p.name,ViewerProfiles.AVATARS[j]); preview.color=index; avatar(previews.get(j),preview); }
            });
            swatch.setOnKeyListener((v,key,event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                if (key == android.view.KeyEvent.KEYCODE_DPAD_UP) { cells.get(animals.length-4+(index%8)/2).requestFocus(); return true; }
                if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN) { saveButton[0].requestFocus(); return true; }
                if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT) { colorCells.get(Math.max(0,index-1)).requestFocus(); return true; }
                if (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) { colorCells.get(Math.min(colors.length-1,index+1)).requestFocus(); return true; }
                return false;
            });
        }
        LinearLayout actions=new LinearLayout(activity); actions.setBaselineAligned(false); panel.addView(actions);
        TextView back=backButton(actions,backAction);
        TextView save = text(confirm,16); save.setGravity(android.view.Gravity.CENTER);
        styleButton(save); save.setFocusableInTouchMode(true); save.setClickable(true);
        for (TextView button:new TextView[]{back,save}) { LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(46),1); size.setMargins(dp(4),dp(12),dp(4),dp(4)); if (button==save) actions.addView(button,size); else button.setLayoutParams(size); }
        save.setOnClickListener(v -> saveAppearance.accept(draftAvatar[0],draftColor[0]));
        saveButton[0] = save;
        saveButton[0].setOnKeyListener((v,key,event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (key == android.view.KeyEvent.KEYCODE_DPAD_UP) { colorCells.get(draftColor[0]).requestFocus(); return true; }
            if (key == android.view.KeyEvent.KEYCODE_DPAD_LEFT) { back.requestFocus(); return true; }
            return key == android.view.KeyEvent.KEYCODE_DPAD_DOWN || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        back.setOnKeyListener((v,key,event) -> { if (event.getAction()!=android.view.KeyEvent.ACTION_DOWN) return false; if (key==android.view.KeyEvent.KEYCODE_DPAD_UP) { colorCells.get(draftColor[0]).requestFocus(); return true; } if (key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT) { save.requestFocus(); return true; } return false; });
        int selected = java.util.Arrays.asList(ViewerProfiles.AVATARS).indexOf(p.avatar);
        show(panel,cells.get(Math.max(0,selected)),account);
        backOnCancel(backAction);
        colorScroll.post(() -> colorScroll.scrollTo(Math.max(0,dp(p.color*48+24)-colorScroll.getWidth()/2),0));
    }
}
