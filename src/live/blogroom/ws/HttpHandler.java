package live.blogroom.ws;

import com.ugnich.onelinesql.OneLineSQL;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import java.net.URI;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author ugnich
 */
public class HttpHandler extends ChannelInboundHandlerAdapter {

    private Connection sql;
    private static Map<Integer, Room> rooms = new ConcurrentHashMap<Integer, Room>();
    int room_id = 0;
    String hash = "";

    public HttpHandler(Connection sql) {
        super();
        this.sql = sql;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        System.out.println("added");
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext context) throws Exception {
        System.out.println("Removed");
        if (room_id > 0) {
            Room r = rooms.get(room_id);
            if (r != null && r.channelGroup != null) {
                r.channelGroup.remove(context.channel());
                if (r.channelGroup.isEmpty()) {
                    r.channelGroup = null;
                    rooms.remove(room_id);
                } else {
                    broadcastStatus(context);
                }
            }
        }
        super.handlerRemoved(context);
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            handleHttp(context, (HttpRequest) msg);
        } else if (msg instanceof TextWebSocketFrame) {
            handleTextFrame(context, (TextWebSocketFrame) msg);
        } else if (msg instanceof PingWebSocketFrame) {
            handlePingFrame(context, (PingWebSocketFrame) msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        cause.printStackTrace();
        context.close();
    }

    private void handleHttp(ChannelHandlerContext context, HttpRequest request) {
        HttpHeaders headers = request.headers();
        if ((headers.get("Connection") != null && headers.get("Connection").equalsIgnoreCase("Upgrade"))
                || (headers.get("Upgrade") != null && headers.get("Upgrade").equalsIgnoreCase("WebSocket"))) {
            try {
                URI uri = new URI(request.uri());
                room_id = Integer.parseInt(uri.getPath().substring("/ws/".length()));
                if (uri.getQuery() != null && uri.getQuery().startsWith("hash=") && uri.getQuery().length() == 37) {
                    hash = uri.getQuery().substring(5);
                }
            } catch (Exception e) {
                System.err.println(e.toString());
            }
            if (room_id > 0) {
                Room r = rooms.get(room_id);
                if (r == null) {
                    r = new Room(room_id);
                    r.loadHistoryFromSQL(sql);
                    rooms.put(room_id, r);
                }
                r.channelGroup.add(context.channel());
                handleHandshake(context, request);
                broadcastStatus(context);
                sendHistory(context, r);
            }
        }
    }

    private void handleHandshake(ChannelHandlerContext context, HttpRequest request) {
        String wsurl = "ws://" + request.headers().get("Host") + request.uri();
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(wsurl, null, true);
        WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(request);
        if (handshaker != null) {
            handshaker.handshake(context.channel(), request);
        } else {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(context.channel());
        }
    }

    private void handlePingFrame(ChannelHandlerContext context, PingWebSocketFrame frame) {
        context.channel().writeAndFlush(new PongWebSocketFrame((frame.content())));
    }

    private void handleTextFrame(ChannelHandlerContext context, TextWebSocketFrame frame) {
        String txt = frame.text().trim();
        if (!txt.isEmpty()) {
            Message msg = new Message();
            msg.text = txt;
            msg.comment_id = Long.toString(System.currentTimeMillis());
            broadcast(context, new TextWebSocketFrame(msg.toJSON().toString()));
            rooms.get(room_id).addHistory(msg);
            if (OneLineSQL.execute(sql, "INSERT INTO comments(post_id,comment_id,user_hash,txt) VALUES (?,?,?,?)", room_id, msg.comment_id, hash, txt) > 0) {
                OneLineSQL.execute(sql, "UPDATE posts SET comments_cnt=comments_cnt+1 WHERE post_id=?", room_id);
            }
        }
    }

    private void broadcast(ChannelHandlerContext context, WebSocketFrame frame) {
        if (room_id > 0) {
            ChannelGroup cg = rooms.get(room_id).channelGroup;
            for (Channel channel : cg) {
                channel.writeAndFlush(frame.retainedDuplicate());
            }
            System.out.println("  - sent to " + cg.size() + " clients");
        }
    }

    private void broadcastStatus(ChannelHandlerContext context) {
        if (room_id > 0) {
            ChannelGroup cg = rooms.get(room_id).channelGroup;
            TextWebSocketFrame statusFrame = new TextWebSocketFrame("{\"status\":{\"online\":" + cg.size() + "}}");
            for (Channel channel : cg) {
                channel.writeAndFlush(statusFrame.retainedDuplicate());
            }
        }
    }

    private void sendHistory(ChannelHandlerContext context, Room room) {
        for (Message msg : room.history) {
            context.channel().write(new TextWebSocketFrame(msg.toJSON().toString()));
        }
        context.channel().flush();
    }
}
