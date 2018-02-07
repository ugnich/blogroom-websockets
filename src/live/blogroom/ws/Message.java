package live.blogroom.ws;

import org.json.JSONObject;

/**
 *
 * @author ugnich
 */
public class Message {

    public String comment_id = null;
    public String text = null;

    public JSONObject toJSON() {
        JSONObject jsonRoot = new JSONObject();
        try {
            JSONObject jsonMessage = new JSONObject();
            if (comment_id != null) {
                jsonMessage.put("comment_id", comment_id);
            }
            if (text != null) {
                jsonMessage.put("text", text);
            }
            jsonRoot.put("message", jsonMessage);
        } catch (Exception e) {
        }
        return jsonRoot;
    }
}
