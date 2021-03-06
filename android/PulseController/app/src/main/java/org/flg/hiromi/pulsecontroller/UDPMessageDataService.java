package org.flg.hiromi.pulsecontroller;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.flg.hiromi.pulsecontroller.UDPMessage.FIELD_COMMAND;
import static org.flg.hiromi.pulsecontroller.UDPMessage.FIELD_DATA;
import static org.flg.hiromi.pulsecontroller.UDPMessage.FIELD_LABEL;
import static org.flg.hiromi.pulsecontroller.UDPMessage.FIELD_RECEIVER;
import static org.flg.hiromi.pulsecontroller.UDPMessage.FIELD_TAG;
import static org.flg.hiromi.pulsecontroller.UDPMessage.FIELD_TYPE;
import static org.flg.hiromi.pulsecontroller.UDPMessage.TABLE_NAME;

import static org.flg.hiromi.pulsecontroller.Pulse.*;

public class UDPMessageDataService extends Service {
    public UDPMessageDataService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(PULSE, "Binding UDPMessage Service");
        return new UDPMessageContext(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(PULSE, "Unbinding UDPMessage Service");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(PULSE, "Destroying UDPMessage Service");
        super.onDestroy();
    }

    /**
     * Wraps a {@link Context} object and caches the UDPMessage data
     */

    public static class UDPMessageContext extends Binder implements IUDPMessageContext {
        private final Context context;
        public UDPMessageContext(Context ctx) {
            context = ctx;
        }

        private Map<String,UDPMessage> allMessages;
        private List<UDPMessage> allMessageList;

        private String[] receiverNames;
        private String[] commandNames;

        private UDPMessageDBHelper opener;

        @Override
        public Map<String,UDPMessage> getMessageMap() {
            if (allMessages == null) {
                allMessages = UDPMessage.loadMessageMap(context);
            }
            return allMessages;
        }

        @Override
        public List<UDPMessage> getMessageList() {
            if (allMessageList == null) {
                allMessageList = new ArrayList<>(getMessageMap().values());
            }
            return allMessageList;
        }

        @Override
        public String[] getReceiverNames() {
            if (receiverNames == null) {
                receiverNames = context.getResources().getStringArray(R.array.module_ids);
                if (receiverNames == null) {
                    throw new Error("Missing redeiver ID resource");
                }
            }
            return receiverNames;
        }

        @Override
        public String[] getCommandNames() {
            if (commandNames == null) {
                commandNames = context.getResources().getStringArray(R.array.pulse_cmds);
                if (commandNames == null) {
                    throw new Error("Missing redeiver ID resource");
                }
            }
            return commandNames;
        }

        public String getLabel(String tag) {
            UDPMessage msg = getMessageMap().get(tag);
            if (msg != null) {
                return msg.getLabel();
            }
            return null;
        }

        @Override
        public String getReceiverName(int id) {
            String[] names = getReceiverNames();
            if (id == 255) {
                id =names.length - 1;
            }
            if (id < names.length) {
                return names[id];
            }
            return "Unknown-" + id;
        }

        @Override
        public String getCommandName(int id) {
            String [] names = getCommandNames();
            if (id < names.length) {
                return names[id];
            }
            return "Unknwon-" + id;
        }

        private UDPMessageDBHelper getDBHelper() {
            if (opener == null) {
                opener = new UDPMessageDBHelper(context);
            }
            return opener;
        }

        @Override
        public void save(UDPMessage msg) {
            Log.i(PULSE, "Saving Msg " + msg.getTag());
            // If we're back to the original, just clean up, so we pick up future changes.
            if (!msg.isOverride()) {
                revert(msg);
            } else {
                try (SQLiteDatabase db = getDBHelper().getWritableDatabase()) {
                    ContentValues values = new ContentValues();
                    values.put(FIELD_TAG, msg.getTag());
                    values.put(FIELD_TYPE, msg.getType());
                    values.put(FIELD_RECEIVER, msg.getReceiverId());
                    values.put(FIELD_COMMAND, msg.getCommandId());
                    if (!msg.getNeedsData()) {
                        values.put(FIELD_DATA, msg.getData());
                    }
                    if (msg.getLabel() != null) {
                        values.put(FIELD_LABEL, msg.getLabel());
                    }
                    db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
                update(msg);
            }
        }

        // We need to update our in-memory copy after saving or reverting.
        private void update(UDPMessage msg) {
            UDPMessage local = getMessageMap().get(msg.getTag());
            if (local != null) {
                local.set(msg);
            } else {
                throw new Error("Missing message in map: " + msg.getTag());
            }
        }

        @Override
        public void revert(UDPMessage msg) {
            Log.i(PULSE, "Reverting Msg " + msg.getTag());
            msg.revert();
            update(msg);
            try (SQLiteDatabase db = getDBHelper().getWritableDatabase()) {
                db.delete(TABLE_NAME, "tag=?", new String[] { msg.getTag() });
            }
        }
    }
}
