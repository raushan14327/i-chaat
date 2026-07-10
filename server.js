/**
 * i chat - Real-time Companion Server
 * 
 * This is the complete production-grade companion Node.js backend server for "i chat".
 * Built with Express, Socket.IO, and a persistent JSON database.
 * 
 * Features:
 * 1. Real-time bi-directional messaging (peer-to-peer & group).
 * 2. User online presence & dynamic "typing..." indicators.
 * 3. Group Admin Rules: Only the group creator (admin) can edit group details or remove members.
 * 4. Shareable Group Invite Links with Instant Joining REST APIs.
 */

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const BASE_URL = process.env.BASE_URL || `http://localhost:${PORT}`;

const app = express();
app.use(cors());
app.use(express.json({ limit: '10mb' }));

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST", "PUT", "DELETE"]
  }
});

// --- PERSISTENCE LAYER (db.json) ---
const DB_FILE = path.join(__dirname, 'db.json');

// Initialize database template
const defaultDb = {
  users: [],         // User objects
  messages: [],      // Message objects
  groups: [],        // Group objects
  group_members: []  // { groupId, userId, joinedAt } objects
};

function readDb() {
  try {
    if (!fs.existsSync(DB_FILE)) {
      fs.writeFileSync(DB_FILE, JSON.stringify(defaultDb, null, 2));
      return defaultDb;
    }
    const data = fs.readFileSync(DB_FILE, 'utf8');
    return JSON.parse(data);
  } catch (err) {
    console.error("Error reading database file:", err);
    return defaultDb;
  }
}

function writeDb(db) {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));
  } catch (err) {
    console.error("Error writing to database file:", err);
  }
}

// Ensure DB is initialized
readDb();

// --- REST ENDPOINTS ---

// 1. User Management
app.post('/api/users', (req, res) => {
  const { userId, username, bio, phoneOrEmail, profilePicBase64 } = req.body;
  if (!userId || !username) {
    return res.status(400).json({ error: "userId and username are required." });
  }

  const db = readDb();
  let user = db.users.find(u => u.userId === userId);
  
  if (user) {
    // Update existing user properties
    user.username = username;
    user.bio = bio || user.bio;
    user.phoneOrEmail = phoneOrEmail || user.phoneOrEmail;
    if (profilePicBase64) user.profilePicBase64 = profilePicBase64;
  } else {
    // Create new user
    user = {
      userId,
      username,
      bio: bio || "Hey there! I am using i chat.",
      profilePicBase64: profilePicBase64 || null,
      isOnline: false,
      lastSeen: Date.now(),
      isBlocked: false,
      typingToId: null,
      phoneOrEmail: phoneOrEmail || ""
    };
    db.users.push(user);
  }

  writeDb(db);
  res.status(200).json(user);
});

app.get('/api/users', (req, res) => {
  const db = readDb();
  res.status(200).json(db.users);
});

app.get('/api/users/:userId', (req, res) => {
  const db = readDb();
  const user = db.users.find(u => u.userId === req.params.userId);
  if (!user) return res.status(404).json({ error: "User not found." });
  res.status(200).json(user);
});


// 2. Group Management & Admin Rules
app.post('/api/groups', (req, res) => {
  const { groupId, name, description, createdBy, groupPicBase64 } = req.body;
  if (!groupId || !name || !createdBy) {
    return res.status(400).json({ error: "groupId, name, and createdBy are required fields." });
  }

  const db = readDb();
  if (db.groups.some(g => g.groupId === groupId)) {
    return res.status(400).json({ error: "Group already exists." });
  }

  const newGroup = {
    groupId,
    name,
    description: description || "",
    createdBy,
    createdAt: Date.now(),
    groupPicBase64: groupPicBase64 || null
  };

  db.groups.push(newGroup);
  // Creator is automatically a group member
  db.group_members.push({
    groupId,
    userId: createdBy,
    joinedAt: Date.now()
  });

  writeDb(db);
  res.status(201).json(newGroup);
});

// [ADMIN RULE] Update group details
app.put('/api/groups/:groupId', (req, res) => {
  const { groupId } = req.params;
  const { name, description, groupPicBase64, requesterUserId } = req.body;

  if (!requesterUserId) {
    return res.status(400).json({ error: "requesterUserId is required to verify administrator privileges." });
  }

  const db = readDb();
  const groupIndex = db.groups.findIndex(g => g.groupId === groupId);

  if (groupIndex === -1) {
    return res.status(404).json({ error: "Group not found." });
  }

  const group = db.groups[groupIndex];

  // Admin Check Rule
  if (group.createdBy !== requesterUserId) {
    return res.status(403).json({ error: "Unauthorized. Only the group administrator (creator) can modify settings." });
  }

  // Apply updates
  if (name) group.name = name;
  if (description !== undefined) group.description = description;
  if (groupPicBase64) group.groupPicBase64 = groupPicBase64;

  writeDb(db);

  // Broadcast settings change to all members
  io.to(groupId).emit('group-updated', group);

  res.status(200).json({ message: "Group settings updated successfully.", group });
});

// [ADMIN RULE] Remove a user from a group
app.delete('/api/groups/:groupId/members/:userId', (req, res) => {
  const { groupId, userId } = req.params;
  const { requesterUserId } = req.query; // Admin user running the query

  if (!requesterUserId) {
    return res.status(400).json({ error: "requesterUserId is required to verify permissions." });
  }

  const db = readDb();
  const group = db.groups.find(g => g.groupId === groupId);

  if (!group) {
    return res.status(404).json({ error: "Group not found." });
  }

  // Admin rule: Only group creator can kick members. Users can self-leave (requesterUserId === userId)
  const isSelfLeaving = requesterUserId === userId;
  const isAdmin = group.createdBy === requesterUserId;

  if (!isAdmin && !isSelfLeaving) {
    return res.status(403).json({ error: "Unauthorized. Only the administrator can remove members." });
  }

  const memberIndex = db.group_members.findIndex(m => m.groupId === groupId && m.userId === userId);
  if (memberIndex === -1) {
    return res.status(404).json({ error: "User is not a member of this group." });
  }

  db.group_members.splice(memberIndex, 1);
  writeDb(db);

  // Broadcast eviction to the group room
  io.to(groupId).emit('member-left', { groupId, userId, kickedBy: requesterUserId });

  res.status(200).json({ message: isSelfLeaving ? "Left group successfully." : "Member removed successfully." });
});


// 3. Group Invite Links (Get Invite Details & Handle Joining)

// Retrieve invite details for share card rendering
app.get('/api/groups/:groupId/invite', (req, res) => {
  const { groupId } = req.params;
  const db = readDb();
  const group = db.groups.find(g => g.groupId === groupId);

  if (!group) {
    return res.status(404).json({ error: "Group not found." });
  }

  const inviteLink = `${BASE_URL}/group/join/${groupId}`;
  res.status(200).json({
    groupId: group.groupId,
    name: group.name,
    description: group.description,
    createdBy: group.createdBy,
    inviteLink: inviteLink
  });
});

// Join group via link / ID endpoint
app.post('/api/groups/join/:groupId', (req, res) => {
  const { groupId } = req.params;
  const { userId } = req.body;

  if (!userId) {
    return res.status(400).json({ error: "userId is required to join a group." });
  }

  const db = readDb();
  const group = db.groups.find(g => g.groupId === groupId);
  if (!group) {
    return res.status(404).json({ error: "Invalid invite link. Group does not exist." });
  }

  const isAlreadyMember = db.group_members.some(m => m.groupId === groupId && m.userId === userId);
  if (isAlreadyMember) {
    return res.status(200).json({ message: "User is already a member of this group.", group });
  }

  // Create group member association
  db.group_members.push({
    groupId,
    userId,
    joinedAt: Date.now()
  });

  writeDb(db);

  // Notify active WebSockets
  io.to(groupId).emit('member-joined', { groupId, userId, joinedAt: Date.now() });

  res.status(200).json({ message: "Successfully joined group.", group });
});

// Retrieve members of a group
app.get('/api/groups/:groupId/members', (req, res) => {
  const { groupId } = req.params;
  const db = readDb();

  const isGroupExist = db.groups.some(g => g.groupId === groupId);
  if (!isGroupExist) {
    return res.status(404).json({ error: "Group not found." });
  }

  const memberIds = db.group_members
    .filter(m => m.groupId === groupId)
    .map(m => m.userId);

  const members = db.users.filter(u => memberIds.includes(u.userId));
  res.status(200).json(members);
});


// 4. Message Sync & History APIs
app.get('/api/messages', (req, res) => {
  const { senderId, receiverId, isGroup } = req.query;
  const db = readDb();

  let filteredMessages = db.messages;

  if (isGroup === 'true') {
    // Group messages are stored with receiverId = groupId
    filteredMessages = filteredMessages.filter(m => m.isGroupMessage && m.receiverId === receiverId);
  } else if (senderId && receiverId) {
    // Direct messages
    filteredMessages = filteredMessages.filter(m => 
      !m.isGroupMessage && 
      ((m.senderId === senderId && m.receiverId === receiverId) || 
       (m.senderId === receiverId && m.receiverId === senderId))
    );
  }

  res.status(200).json(filteredMessages);
});

// Send message via REST API (fallback / alternative to WS)
app.post('/api/messages', (req, res) => {
  const message = req.body;
  if (!message.messageId || !message.senderId || !message.receiverId || !message.text) {
    return res.status(400).json({ error: "Missing required message parameters." });
  }

  const db = readDb();
  db.messages.push(message);
  writeDb(db);

  // Broadcast to target sockets
  if (message.isGroupMessage) {
    io.to(message.receiverId).emit('receive-message', message);
  } else {
    io.to(message.receiverId).emit('receive-message', message);
    io.to(message.senderId).emit('message-status-updated', { messageId: message.messageId, status: "SENT" });
  }

  res.status(201).json(message);
});


// --- REAL-TIME WEBSOCKET ROUTING (Socket.IO) ---
io.on('connection', (socket) => {
  console.log(`Socket Client Connected: ${socket.id}`);

  // 1. User Presence Registration
  socket.on('register-presence', ({ userId }) => {
    socket.userId = userId;
    const db = readDb();
    const user = db.users.find(u => u.userId === userId);
    if (user) {
      user.isOnline = true;
      user.lastSeen = Date.now();
      writeDb(db);

      // Broadcast presence change to everyone
      io.emit('user-presence-changed', { userId, isOnline: true, lastSeen: user.lastSeen });
    }
    // Also join user to their personal push room for direct messages
    socket.join(userId);
    console.log(`User ${userId} registered in personal room.`);
  });

  // 2. Group Room Joining
  socket.on('join-group-room', ({ groupId }) => {
    socket.join(groupId);
    console.log(`Socket ${socket.id} joined group room: ${groupId}`);
  });

  // 3. Real-time Typing Status Indicator
  socket.on('typing-status', ({ senderId, receiverId, isGroup, isTyping }) => {
    const targetRoom = receiverId;
    socket.to(targetRoom).emit('user-typing', {
      senderId,
      receiverId,
      isGroup,
      isTyping
    });
  });

  // 4. Real-time Message Relay
  socket.on('send-message', (message) => {
    const db = readDb();
    db.messages.push(message);
    writeDb(db);

    if (message.isGroupMessage) {
      // Send to all group room members except sender
      socket.to(message.receiverId).emit('receive-message', message);
    } else {
      // Send to direct recipient personal room
      socket.to(message.receiverId).emit('receive-message', message);
      // Confirm SENT status to sender
      io.to(message.senderId).emit('message-status-updated', { messageId: message.messageId, status: "SENT" });
    }
  });

  // 5. Message Status Tracking (DELIVERED, READ)
  socket.on('update-message-status', ({ messageId, status, senderId }) => {
    const db = readDb();
    const message = db.messages.find(m => m.messageId === messageId);
    if (message) {
      message.status = status;
      writeDb(db);
      
      // Notify original sender of delivery update
      io.to(senderId).emit('message-status-updated', { messageId, status });
    }
  });

  // 6. Message Reactions
  socket.on('add-reaction', ({ messageId, reaction, isSenderReaction, targetRoom }) => {
    const db = readDb();
    const message = db.messages.find(m => m.messageId === messageId);
    if (message) {
      if (isSenderReaction) {
        message.senderReaction = reaction;
      } else {
        message.receiverReaction = reaction;
      }
      writeDb(db);

      // Broadcast reaction change to active channel
      socket.to(targetRoom).emit('reaction-added', { messageId, reaction, isSenderReaction });
    }
  });

  // 7. Message Deletion
  socket.on('delete-message', ({ messageId, isEveryone, targetRoom }) => {
    const db = readDb();
    const message = db.messages.find(m => m.messageId === messageId);
    if (message) {
      if (isEveryone) {
        message.isDeletedForEveryone = true;
        message.text = "This message was deleted";
      } else {
        message.isDeletedForMe = true;
      }
      writeDb(db);

      socket.to(targetRoom).emit('message-deleted', { messageId, isEveryone });
    }
  });

  // 8. Disconnect Presence Reset
  socket.on('disconnect', () => {
    console.log(`Socket Client Disconnected: ${socket.id}`);
    if (socket.userId) {
      const db = readDb();
      const user = db.users.find(u => u.userId === socket.userId);
      if (user) {
        user.isOnline = false;
        user.lastSeen = Date.now();
        writeDb(db);

        // Broadcast offline status to everyone
        io.emit('user-presence-changed', { userId: socket.userId, isOnline: false, lastSeen: user.lastSeen });
      }
    }
  });
});

// Start listening
server.listen(PORT, () => {
  console.log(`========================================`);
  console.log(`🚀 i chat Real-Time Server running!`);
  console.log(`🔗 API Server: ${BASE_URL}`);
  console.log(`🔌 WebSocket Gateway: ${BASE_URL}`);
  console.log(`📂 Database stored: ${DB_FILE}`);
  console.log(`========================================`);
});
