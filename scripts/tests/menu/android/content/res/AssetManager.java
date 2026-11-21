package android.content.res;

public class AssetManager {
    public static java.nio.file.Path root;
    public java.io.InputStream open(String name)throws java.io.IOException {
        if(root==null)throw new java.io.IOException("test asset unavailable");
        return java.nio.file.Files.newInputStream(root.resolve(name));
    }
}
