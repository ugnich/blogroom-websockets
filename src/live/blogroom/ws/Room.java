package live.blogroom.ws;

import com.ugnich.onelinesql.OneLineSQL;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 *
 * @author ugnich
 */
public class Room {

    public int room_id = 0;
    public ChannelGroup channelGroup = null;
    public ArrayList<Message> history = null;

    public Room(int room_id) {
        this.room_id = room_id;
        channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        history = new ArrayList<Message>();
    }

    public void addHistory(Message msg) {
        history.add(msg);
        if (history.size() > 10) {
            history.remove(0);
        }
    }

    public void loadHistoryFromSQL(Connection sql) {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = sql.prepareStatement("SELECT comment_id,txt FROM (SELECT comment_id,txt FROM comments WHERE post_id=? ORDER BY comment_id DESC LIMIT 10) AS t ORDER BY comment_id ASC");
            stmt.setInt(1, room_id);
            rs = stmt.executeQuery();
            rs.beforeFirst();
            while (rs.next()) {
                Message msg = new Message();
                msg.comment_id = rs.getString(1);
                msg.text = rs.getString(2);
                history.add(msg);
            }
        } catch (SQLException e) {
            System.err.println(e);
        } finally {
            OneLineSQL.finishSQL(rs, stmt);
        }
    }
}
