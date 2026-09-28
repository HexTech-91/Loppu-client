package dev.luminous.core.impl;

import dev.luminous.core.Manager;
import dev.luminous.mod.modules.impl.misc.AutoTK;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.PlayerEntity;
import org.apache.commons.io.IOUtils;

public class FriendManager
extends Manager {
    // LinkedHashMap preserves insertion order; value=true = '+' (has permission), value=false = '-' (no permission)
    public final LinkedHashMap<String, Boolean> friends = new LinkedHashMap<>();

    // Backward-compatible list for modules that only check membership
    public final ArrayList<String> friendList = new ArrayList();

    public FriendManager() {
        this.read();
    }

    // Sync friendList from friends map (only entries with value=true)
    private void syncFriendList() {
        friendList.clear();
        for (Map.Entry<String, Boolean> entry : friends.entrySet()) {
            if (entry.getValue()) {
                friendList.add(entry.getKey());
            }
        }
    }

    public boolean isFriend(String name) {
        if (AutoTK.isIgnoringFriends()) {
            return false;
        }
        Boolean status = friends.get(name);
        return status != null && status;
    }

    public boolean isFriend(PlayerEntity entity) {
        return this.isFriend(entity.getGameProfile().getName());
    }

    public void remove(String name) {
        friends.remove(name);
        friendList.remove(name);
    }

    public void add(String name) {
        if (!friends.containsKey(name)) {
            friends.put(name, true);
            friendList.add(name);
        } else {
            // If already exists but was '-', set to '+'
            friends.put(name, true);
            syncFriendList();
        }
    }

    public void friend(PlayerEntity entity) {
        this.friend(entity.getGameProfile().getName());
    }

    public void friend(String name) {
        if (friends.containsKey(name)) {
            // Toggle permission
            boolean current = friends.get(name);
            friends.put(name, !current);
            syncFriendList();
        } else {
            friends.put(name, true);
            friendList.add(name);
        }
    }

    /**
     * Toggle a specific friend's permission status.
     * @param name player name
     * @return the new status (true = '+' has permission, false = '-' no permission)
     */
    public boolean togglePermission(String name) {
        if (!friends.containsKey(name)) {
            return false;
        }
        boolean current = friends.get(name);
        boolean newStatus = !current;
        friends.put(name, newStatus);
        syncFriendList();
        return newStatus;
    }

    /**
     * Get the permission status of a friend.
     * @param name player name
     * @return Boolean.TRUE = '+' has permission, Boolean.FALSE = '-' no permission, null = not a friend
     */
    public Boolean getStatus(String name) {
        return friends.get(name);
    }

    /**
     * Check if a name is in the friends list regardless of permission status.
     */
    public boolean isInList(String name) {
        return friends.containsKey(name);
    }

    public void read() {
        try {
            File friendFile = FriendManager.getFile("friends.txt");
            if (!friendFile.exists()) {
                return;
            }
            friends.clear();
            friendList.clear();
            List<String> list = IOUtils.readLines((InputStream)new FileInputStream(friendFile), (Charset)StandardCharsets.UTF_8);
            for (String s : list) {
                String trimmed = s.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.endsWith("+")) {
                    String name = trimmed.substring(0, trimmed.length() - 1);
                    friends.put(name, true);
                    friendList.add(name);
                } else if (trimmed.endsWith("-")) {
                    String name = trimmed.substring(0, trimmed.length() - 1);
                    friends.put(name, false);
                } else {
                    // Backward compatibility: old format (just a name, no suffix) → treat as '+'
                    friends.put(trimmed, true);
                    friendList.add(trimmed);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            File friendFile = FriendManager.getFile("friends.txt");
            PrintWriter printwriter = new PrintWriter(new OutputStreamWriter((OutputStream)new FileOutputStream(friendFile), StandardCharsets.UTF_8));
            for (Map.Entry<String, Boolean> entry : this.friends.entrySet()) {
                String suffix = entry.getValue() ? "+" : "-";
                printwriter.println(entry.getKey() + suffix);
            }
            printwriter.close();
        }
        catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("[Alien] Failed to save friends");
        }
    }
}