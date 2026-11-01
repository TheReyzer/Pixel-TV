package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** TV wizard; edits stay in a draft until a successful search replaces the active results. */
final class DiscoveryController {
    interface Callback { void found(DiscoveryQuery query,List<MediaRepository.MediaItem> items,boolean online,String notice); }
    private final Activity activity;
    private final ProfileController ui;
    private final MediaRepository repo;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> pending;
    private int generation,step;
    private boolean destroyed;
    private DiscoveryQuery draft;
    private View anchor;
    private Callback callback;
    private final android.content.SharedPreferences prefs;
    DiscoveryController(Activity activity,ProfileController ui,MediaRepository repo) {
        this.activity=activity; this.ui=ui; this.repo=repo;
        prefs=activity.getSharedPreferences("viewer_discovery_v1",Activity.MODE_PRIVATE);
        com.airbnb.lottie.LottieCompositionFactory.fromRawRes(activity,R.raw.movie_discovery);
    }
    void destroy() { destroyed=true; cancelSearch(); worker.shutdownNow(); }
    private void cancelSearch() { generation++; if (pending!=null) pending.cancel(true); pending=null; }
    void open(DiscoveryQuery previous,View anchor,Callback callback) {
        cancelSearch(); this.anchor=anchor; this.callback=callback;
        draft=previous==null?new DiscoveryQuery(ui.model.selected().id):new DiscoveryQuery(previous);
        render(0);
    }
    private TextView button(String label) {
        TextView v=ui.text(label,16); v.setGravity(android.view.Gravity.CENTER); v.setPadding(ui.dp(12),0,ui.dp(12),0);
        ui.styleButton(v);
        v.setFocusableInTouchMode(true); v.setClickable(true); return v;
    }
    private View choice(LinearLayout rows,String label,String subtitle,boolean checked,ViewerProfiles.Profile person,int symbol,Runnable action) {
        View row=activity.getLayoutInflater().inflate(R.layout.item_watch_app,rows,false);
        row.setFocusableInTouchMode(true); row.setSelected(checked); row.setBackgroundResource(R.drawable.discovery_choice_bg);
        TextView title=row.findViewById(R.id.title); title.setText(label+(checked?"  ✓":""));
        title.setTextColor(activity.getResources().getColorStateList(R.color.discovery_choice_text,activity.getTheme()));
        TextView hint=row.findViewById(R.id.status); hint.setText(subtitle); hint.setVisibility(subtitle==null?View.GONE:View.VISIBLE);
        hint.setTextColor(activity.getResources().getColorStateList(R.color.discovery_choice_text_dim,activity.getTheme()));
        ImageView icon=row.findViewById(R.id.icon);
        if (person!=null) ui.avatar(icon,person);
        else if (symbol==0) icon.setVisibility(View.GONE);
        else { icon.setImageResource(symbol); icon.setScaleType(ImageView.ScaleType.FIT_CENTER); icon.setPadding(ui.dp(3),ui.dp(3),ui.dp(3),ui.dp(3)); icon.setDuplicateParentStateEnabled(true); icon.setImageTintList(title.getTextColors()); }
        row.setContentDescription(label+(checked?", selecionado":"")+(subtitle==null?"":", "+subtitle));
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,ui.dp(54)); layout.setMargins(0,ui.dp(4),0,ui.dp(4)); rows.addView(row,layout); return row;
    }
    private TextView chip(String label,int icon,boolean selected) {
        TextView chip=ui.text(label,14); chip.setSingleLine(true); chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(ui.dp(12),0,ui.dp(12),0); chip.setCompoundDrawablePadding(ui.dp(7));
        chip.setBackgroundResource(R.drawable.discovery_choice_bg); chip.setTextColor(activity.getResources().getColorStateList(R.color.discovery_choice_text,activity.getTheme()));
        chip.setCompoundDrawableTintList(chip.getTextColors()); chip.setFocusableInTouchMode(true); chip.setClickable(true);
        android.graphics.drawable.Drawable image=activity.getDrawable(icon); image.setBounds(0,0,ui.dp(23),ui.dp(23));
        chip.setCompoundDrawablesRelative(image,null,null,null); checkChip(chip,selected); return chip;
    }
    private void checkChip(TextView chip,boolean selected) {
        android.graphics.drawable.Drawable check=selected?activity.getDrawable(R.drawable.ic_check):new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
        check.setBounds(0,0,ui.dp(15),ui.dp(15));
        android.graphics.drawable.Drawable[] icons=chip.getCompoundDrawablesRelative();
        chip.setCompoundDrawablesRelative(icons[0],icons[1],check,icons[3]); chip.setSelected(selected);
        chip.setContentDescription(chip.getText()+(selected?", selecionado":""));
    }
    private static final int[] GENRE_ICONS={R.drawable.ic_genre_action,R.drawable.ic_genre_adventure,R.drawable.ic_genre_animation,R.drawable.ic_profile,
            R.drawable.ic_genre_comedy,R.drawable.ic_genre_crime,R.drawable.ic_genre_documentary,R.drawable.ic_genre_drama,R.drawable.ic_genre_family,
            R.drawable.ic_genre_fantasy,R.drawable.ic_genre_history,R.drawable.ic_genre_horror,R.drawable.ic_genre_mystery,R.drawable.ic_genre_romance,
            R.drawable.ic_genre_scifi,R.drawable.ic_genre_sport,R.drawable.ic_genre_thriller,R.drawable.ic_genre_war,R.drawable.ic_genre_western};
    private void render(int nextStep) {
        step=nextStep;
        String[] titles={"Quem vai assistir?","Filme ou série?","Quais gêneros?","De qual época?","Incluir assistidos?"};
        String[] hints={"Geral inclui todos. Ou marque uma ou mais pessoas.","Escolha o formato para esta descoberta.","Marque um ou mais. Basta combinar com um dos gêneros escolhidos.","A época considera o ano de lançamento.","Sem assistidos exclui todo o histórico das pessoas escolhidas."};
        LinearLayout panel=ui.panel(titles[step],hints[step]);
        TextView heading=(TextView)panel.getChildAt(0); panel.removeViewAt(0);
        LinearLayout header=new LinearLayout(activity); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView progress=ui.text("Descobrir · "+(step+1)+" de 5",12); progress.setTextColor(0xffbdbdc2); progress.setPadding(ui.dp(8),0,ui.dp(10),0);
        header.addView(progress); panel.addView(header,0);
        LinearLayout rows=ui.rows(panel); View[] focus={null};
        if (step==0) for (ViewerProfiles.Profile person:ui.model.profiles()) {
            View row=choice(rows,person.name,person.aggregate()?"Combinar os gostos de todos":"Perfil individual",draft.people.contains(person.id),person,0,() -> {
                draft.togglePerson(person.id);
                for (int i=0;i<rows.getChildCount();i++) { View option=rows.getChildAt(i); ViewerProfiles.Profile p=ui.model.find((String)option.getTag());
                    ui.checkPersonRow(option,p.name,p.aggregate()?"Combinar os gostos de todos":"Perfil individual",draft.people.contains(p.id)); }
            }); row.setTag(person.id);
            if (draft.people.contains(person.id)&&focus[0]==null) focus[0]=row;
        }
        else if (step==1) {
            LinearLayout formats=new LinearLayout(activity); formats.setPadding(ui.dp(4),ui.dp(10),ui.dp(4),ui.dp(18)); rows.addView(formats);
            for (String type:new String[]{"movie","series"}) {
                LinearLayout card=new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(android.view.Gravity.CENTER);
                card.setBackgroundResource(R.drawable.discovery_choice_bg); card.setFocusableInTouchMode(true); card.setClickable(true); card.setSelected(draft.type.equals(type));
                ImageView icon=new ImageView(activity); icon.setImageResource(type.equals("movie")?R.drawable.ic_video:R.drawable.ic_livetv); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setImageTintList(activity.getResources().getColorStateList(R.color.discovery_choice_text,activity.getTheme())); icon.setDuplicateParentStateEnabled(true);
                card.addView(icon,new LinearLayout.LayoutParams(ui.dp(32),ui.dp(32)));
                TextView label=ui.text(type.equals("movie")?"Filme":"Série",14); label.setSingleLine(true); label.setIncludeFontPadding(false); label.setDuplicateParentStateEnabled(true);
                label.setTextColor(activity.getResources().getColorStateList(R.color.discovery_choice_text,activity.getTheme()));
                LinearLayout.LayoutParams caption=new LinearLayout.LayoutParams(-2,-2); caption.setMargins(0,10,0,0); card.addView(label,caption);
                LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,ui.dp(100),1); if (type.equals("series")) layout.setMargins(ui.dp(8),0,0,0); formats.addView(card,layout);
                card.setContentDescription(label.getText()+(draft.type.equals(type)?", selecionado":""));
                card.setTag(type); card.setOnClickListener(v -> {
                    draft.type=type; render(2);
                });
                if (draft.type.equals(type)) focus[0]=card;
            }
        }
        else if (step==2) {
            DiscoveryFlowLayout genres=new DiscoveryFlowLayout(activity); genres.setPadding(ui.dp(4),ui.dp(8),ui.dp(4),ui.dp(14)); rows.addView(genres,new LinearLayout.LayoutParams(-1,-2));
            TextView any=chip("Qualquer gênero",R.drawable.ic_genre_all,draft.genres.isEmpty()); genres.addView(any);
            Runnable refresh=() -> { checkChip((TextView)genres.getChildAt(0),draft.genres.isEmpty()); for (int i=1;i<genres.getChildCount();i++) checkChip((TextView)genres.getChildAt(i),draft.genres.contains((String)genres.getChildAt(i).getTag())); };
            any.setOnClickListener(v -> { draft.genres.clear(); refresh.run(); });
            if (draft.genres.isEmpty()) focus[0]=any;
            for (int i:DiscoveryQuery.alphabeticalGenres()) {
                String genre=DiscoveryQuery.GENRES[i];
                TextView row=chip(DiscoveryQuery.GENRE_NAMES[i],GENRE_ICONS[i],draft.genres.contains(genre)); row.setTag(genre); genres.addView(row);
                row.setOnClickListener(v -> { if (!draft.genres.remove(genre)) draft.genres.add(genre); refresh.run(); });
                if (draft.genres.contains(genre)&&focus[0]==null) focus[0]=row;
            }
        } else if (step==3) for (int i=0;i<DiscoveryQuery.ERAS.length;i++) {
            final int era=i; String[] details={"Ano atual e os dois anteriores","Qualquer época","Mais de 15 anos desde o lançamento"};
            int[] icons={R.drawable.ic_discovery_recent,R.drawable.ic_genre_all,R.drawable.ic_discovery_classic};
            View row=choice(rows,DiscoveryQuery.ERAS[i],details[i],draft.era==i,null,icons[i],() -> { draft.era=era; render(4); });
            if (draft.era==i) focus[0]=row;
        } else {
            choice(rows,"Não incluir assistidos","Ocultar qualquer título já visto por alguém do grupo",!draft.includeWatched,null,R.drawable.ic_discovery_unwatched,() -> { draft.includeWatched=false; search(); });
            choice(rows,"Incluir também os assistidos","Permitir rever títulos já registrados",draft.includeWatched,null,R.drawable.ic_discovery_watched,() -> { draft.includeWatched=true; search(); });
            focus[0]=rows.getChildAt(draft.includeWatched?1:0);
            TextView summary=ui.text(draft.summary(ui.model),13); summary.setTextColor(0xffdddddf); summary.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12)); rows.addView(summary);
        }
        boolean multiple=step==0||step==2;
        TextView error=ui.text("",13); error.setTextColor(0xffffb7b7); panel.addView(error);
        LinearLayout footer=new LinearLayout(activity); panel.addView(footer);
        TextView back=button(step==0?"Cancelar":"Voltar"),next=button(step==4?"Buscar descobertas":"Avançar");
        footer.addView(back,new LinearLayout.LayoutParams(0,ui.dp(50),1)); if (multiple) footer.addView(next,new LinearLayout.LayoutParams(0,ui.dp(50),1));
        if (multiple) ((LinearLayout.LayoutParams)next.getLayoutParams()).setMargins(ui.dp(8),0,0,0);
        back.setOnClickListener(v -> { if (step==0) ui.dialog.dismiss(); else render(step-1); });
        next.setOnClickListener(v -> { if (draft.people.isEmpty()) { error.setText("Selecione pelo menos uma pessoa."); return; } if (step<4) render(step+1); else search(); });
        View[] lastChoice={focus[0]==null?rows.getChildAt(0):focus[0]};
        for (View row:rows.getFocusables(View.FOCUS_FORWARD)) {
            row.setOnFocusChangeListener((v,focused) -> { if (focused) lastChoice[0]=v; });
            row.setOnKeyListener((v,key,event) -> {
                if (event.getAction()!=android.view.KeyEvent.ACTION_DOWN||step==1||step==2) return false;
                if (key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT) { (multiple?next:back).requestFocus(); return true; }
                if (key==android.view.KeyEvent.KEYCODE_DPAD_LEFT) { back.requestFocus(); return true; }
                return false;
            });
        }
        for (View v:multiple?new View[]{back,next}:new View[]{back}) v.setOnKeyListener((view,key,event) -> {
            if (event.getAction()!=android.view.KeyEvent.ACTION_DOWN) return false;
            if (key==android.view.KeyEvent.KEYCODE_DPAD_UP) { lastChoice[0].requestFocus(); return true; }
            return key==android.view.KeyEvent.KEYCODE_DPAD_DOWN;
        });
        ui.show(panel,focus[0]==null?rows.getChildAt(0):focus[0],anchor);
        ui.dialog.setOnCancelListener(d -> { if (step>0) panel.post(() -> render(step-1)); });
    }
    private void search() {
        cancelSearch(); final int token=generation; final DiscoveryQuery query=new DiscoveryQuery(draft);
        final List<String> previous=java.util.Arrays.asList(prefs.getString("last_first","").split(","));
        LinearLayout panel=ui.panel("Buscando novas descobertas…","Cruzando os gostos e procurando títulos para vocês.");
        ImageView progress=new ImageView(activity); com.airbnb.lottie.LottieDrawable animation=new com.airbnb.lottie.LottieDrawable();
        progress.setContentDescription("Procurando filmes e séries");
        animation.enableMergePathsForKitKatAndAbove(true);
        com.airbnb.lottie.LottieCompositionFactory.fromRawRes(activity,R.raw.movie_discovery)
                .addListener(composition -> { if (token==generation&&!destroyed) { animation.setComposition(composition); progress.setImageDrawable(animation); animation.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE); animation.playAnimation(); } })
                .addFailureListener(error -> android.util.Log.w("TVHomeDiscover","Loading animation unavailable",error));
        progress.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) { animation.cancelAnimation(); }
        });
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(ui.dp(160),ui.dp(135)); size.gravity=android.view.Gravity.CENTER_HORIZONTAL; size.setMargins(0,ui.dp(10),0,ui.dp(10)); panel.addView(progress,size);
        TextView cancel=button("Cancelar busca"); panel.addView(cancel,new LinearLayout.LayoutParams(-1,ui.dp(50)));
        cancel.setOnClickListener(v -> { cancelSearch(); render(4); });
        ui.show(panel,cancel,anchor); ui.dialog.setOnCancelListener(d -> { cancelSearch(); panel.post(() -> render(4)); });
        pending=worker.submit(() -> {
            try {
                MediaRepository.DiscoveryCatalog catalog=repo.discover(query);
                List<MediaRepository.MediaItem> candidates=new java.util.ArrayList<>(catalog.items);
                java.util.Collections.shuffle(candidates);
                MediaRepository.RecommendationBatch batch=repo.recommendDiscovery(candidates,query);
                List<MediaRepository.MediaItem> results=DiscoveryQuery.avoidRepeatedOrder(batch.items,previous);
                if (!results.isEmpty()) {
                    activity.runOnUiThread(() -> { if (!destroyed&&token==generation) ((TextView)panel.getChildAt(1)).setText("Preparando as imagens das primeiras páginas…"); });
                    repo.prepareDiscoveryImages(results);
                }
                activity.runOnUiThread(() -> {
                    if (destroyed||token!=generation||activity.isFinishing()) return;
                    cancelSearch();
                    if (results.isEmpty()) { empty(catalog.online); return; }
                    List<String> first=new java.util.ArrayList<>(); for (int i=0;i<Math.min(3,results.size());i++) first.add(results.get(i).imdbId);
                    prefs.edit().putString("last_first",String.join(",",first)).apply();
                    android.util.Log.d("TVHomeDiscover","RESULTS count="+results.size()+" people="+query.people.size());
                    ui.dialog.dismiss(); callback.found(query,results,catalog.online,batch.notice);
                });
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception e) { activity.runOnUiThread(() -> { if (!destroyed&&token==generation&&!activity.isFinishing()) empty(false); }); }
        });
    }
    private void empty(boolean online) {
        LinearLayout panel=ui.panel("Nenhuma descoberta encontrada",online?"Não encontramos títulos com esta combinação. Altere os filtros e tente novamente.":"Não foi possível ampliar o catálogo pela internet, e o catálogo salvo não tem títulos com esta combinação.");
        TextView edit=button("Rever escolhas"),retry=button("Tentar novamente"); panel.addView(edit,new LinearLayout.LayoutParams(-1,ui.dp(50))); panel.addView(retry,new LinearLayout.LayoutParams(-1,ui.dp(50)));
        edit.setOnClickListener(v -> render(0)); retry.setOnClickListener(v -> search());
        ui.show(panel,edit,anchor); ui.dialog.setOnCancelListener(d -> panel.post(() -> render(4)));
    }
}
