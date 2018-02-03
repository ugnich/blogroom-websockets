package live.blogroom.ws;

import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.util.ArrayList;

/**
 *
 * @author ugnich
 */
public class Room {

    public int room_id = 0;
    public ChannelGroup channelGroup = null;
    public ArrayList<String> history = null;

    public Room(int room_id) {
        this.room_id = room_id;
        channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        history = new ArrayList<String>();
    }

    public void addHistory(String text) {
        history.add(text);
        if (history.size() > 20) {
            history.remove(0);
        }
    }
}
