package wex.ui.core.feature;

import android.os.Bundle;

import java.util.ArrayList;

/** Instance-local page history and scroll offsets; contains no host state. */
final class ModulePageState {
    enum Page { SETTINGS, ABOUT, DIAGNOSTICS, LICENSE, PRIVACY, CREDITS }
    private final ArrayList<Page> history=new ArrayList<>();
    private final int[] offsets=new int[Page.values().length];

    ModulePageState(){history.add(Page.SETTINGS);}
    Page current(){return history.get(history.size()-1);}
    int scroll(){return offsets[current().ordinal()];}
    void scroll(int value){offsets[current().ordinal()]=Math.max(0,value);}
    void open(Page page){if(current()!=page)history.add(page);}
    boolean back(){if(history.size()==1)return false;history.remove(history.size()-1);return true;}
    void save(Bundle bundle){
        StringBuilder pages=new StringBuilder();
        for(Page page:history){if(pages.length()!=0)pages.append(',');pages.append(page.name());}
        bundle.putString("mdl_desktop_pages",pages.toString());bundle.putIntArray("mdl_desktop_scroll",offsets.clone());
    }
    static ModulePageState restore(Bundle bundle){
        ModulePageState result=new ModulePageState();
        if(bundle==null)return result;
        try{
            String saved=bundle.getString("mdl_desktop_pages");
            if(saved==null)return result;
            String[] names=saved.split(",");
            if(names.length<1||names.length>Page.values().length||!Page.SETTINGS.name().equals(names[0]))return result;
            ArrayList<Page> pages=new ArrayList<>();
            for(String name:names){Page page=Page.valueOf(name);if(pages.contains(page))return result;pages.add(page);}
            int[] offsets=bundle.getIntArray("mdl_desktop_scroll");
            result.history.clear();result.history.addAll(pages);
            if(offsets!=null&&offsets.length==result.offsets.length)for(int i=0;i<offsets.length;i++)result.offsets[i]=Math.max(0,offsets[i]);
        }catch(RuntimeException ignored){return new ModulePageState();}
        return result;
    }
}
