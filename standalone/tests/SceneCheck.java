import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.HashSet;
class SceneCheck {
    public static void main(String[] args) throws Exception {
        var frame=ImageIO.read(Path.of(args[0]).toFile());
        if(frame==null||frame.getWidth()<320||frame.getHeight()<180)throw new IllegalArgumentException("Missing game frame");
        var colors=new HashSet<Integer>();int red=0,pixels=0;
        for(int y=0;y<frame.getHeight()*4/5;y+=3)for(int x=0;x<frame.getWidth();x+=3){
            int rgb=frame.getRGB(x,y);colors.add(rgb&0xffffff);pixels++;
            if(((rgb>>16)&255)>190 && ((rgb>>8)&255)<105 && (rgb&255)<110)red++;
        }
        if(colors.size()<100 || red*4>pixels*3)throw new IllegalStateException("Game world not visible; loading overlay");
    }
}
