# i chat — Real-time Android Chat Application & Companion Backend

**i chat** is a visually polished, feature-rich, and modern real-time instant messaging application. It is powered by a high-performance Jetpack Compose Android client and a lightweight, persistent companion Node.js backend. 

With peer-to-peer and group chat support, real-time typing indicators, active presence tracking, message status sync (sent/delivered/read), emoji reactions, and secure Admin Rules, **i chat** delivers a complete and immersive communications experience.

---

## 🚀 Key Features

### 📱 Jetpack Compose Android Client
- **Beautiful Material 3 Design**: Features smooth edge-to-edge screens, elegant color harmonies, ample spacing, clear typography headings, and dark/light dynamic styling.
- **Peer-to-Peer Chats**: Direct instant messaging with active user profiles, online presence indicator lights, and dynamic "typing..." states.
- **Group Channels**: Create custom groups, select names, add descriptions, and easily invite your friends.
- **Group Invite Links**: Tap the header inside any group to view a shareable invite link (`https://chat.app/group/join/group_<id>`) or Group ID. Others can copy-paste this ID/Link to instantly join.
- **Group Admin Rules**: Only the original creator (Administrator) of a group is allowed to update group settings or remove members from the channel.
- **Message Controls**: Long-press messages to add emoji reactions, delete messages for yourself or everyone, or copy text to the clipboard.

### 🔌 Companion Node.js Backend Server
- **REST Web APIs**: Endpoints for handling user profiles, group metadata, group memberships, and historical message sync.
- **Socket.IO Gateways**: Multi-channel WebSocket system coordinating real-time message forwarding, online presence updates, and active typing indicator state relays.
- **Local Persistence Layer**: Simple local flat-file storage engine (`db.json`) ensuring chat threads, users, and groups persist across server restarts.

---

## 🛠️ Companion Backend Setup Guide

The server acts as a real-time gateway and synchronization engine for your Android app.

### Prerequisites
- [Node.js](https://nodejs.org/) (v16 or higher)
- npm (Node Package Manager)

### Installation & Run

1. **Download the Server Files**:
   Ensure you have `server.js` and `package.json` in your project folder.

2. **Install Dependencies**:
   Navigate to the root directory where `package.json` is located and run:
   ```bash
   npm install
   ```

3. **Start the Production Server**:
   ```bash
   npm start
   ```

4. **Start the Development Server (Auto-Reloading)**:
   ```bash
   npm run dev
   ```

The server will initialize a local persistent database file called `db.json` automatically and start listening on:
- **API Server URL**: `http://localhost:3000`
- **Socket.IO Gateway**: `http://localhost:3000`

---

## 📡 Backend API Reference

### 👤 User Endpoints

#### Create / Update User
- **URL**: `POST /api/users`
- **Body**:
  ```json
  {
    "userId": "user_12345",
    "username": "John Doe",
    "bio": "Coding is life.",
    "phoneOrEmail": "john@example.com",
    "profilePicBase64": "data:image/png;base64,..."
  }
  ```

#### List All Users
- **URL**: `GET /api/users`

---

### 👥 Group Endpoints

#### Create Group
- **URL**: `POST /api/groups`
- **Body**:
  ```json
  {
    "groupId": "group_abc987",
    "name": "Android Devs",
    "description": "Jetpack Compose discussion",
    "createdBy": "user_12345"
  }
  ```

#### [Admin Rule] Update Group Details
- **URL**: `PUT /api/groups/:groupId`
- **Body**:
  ```json
  {
    "name": "Updated Android Devs",
    "description": "Discussion about modern Android development",
    "requesterUserId": "user_12345" // Checked against group creator ID
  }
  ```

#### [Admin Rule] Remove Group Member
- **URL**: `DELETE /api/groups/:groupId/members/:userId?requesterUserId=admin_id`
- *Note: If a member removes themselves, `requesterUserId` can equal the `userId` being removed (Leave Group).*

#### Join Group via Shareable Invite Link/ID
- **URL**: `POST /api/groups/join/:groupId`
- **Body**:
  ```json
  {
    "userId": "user_7777"
  }
  ```

#### Get Group Invite Share Card Details
- **URL**: `GET /api/groups/:groupId/invite`

#### Get Group Members
- **URL**: `GET /api/groups/:groupId/members`

---

### 💬 Message Endpoints

#### Get Chat Thread Messages
- **URL**: `GET /api/messages`
- **Query Params**:
  - `senderId` (e.g., `user_123`)
  - `receiverId` (e.g., `user_456` or `group_abc`)
  - `isGroup` (true / false)

---

## 🛜 Socket.IO WebSocket Event Interface

Real-time actions operate via Socket.IO events over the connection gateway:

### Client to Server Events (Emit)
| Event | Payload | Description |
| :--- | :--- | :--- |
| `register-presence` | `{ userId }` | Registers user's active connection state to the network and sets status to online. |
| `join-group-room` | `{ groupId }` | Subscribes current client socket to receive group room event broadcasts. |
| `send-message` | `Message` object | Sends and stores a new chat message. Relayed to receiver. |
| `typing-status` | `{ senderId, receiverId, isGroup, isTyping }` | Relays "typing..." status indicator on the other screen. |
| `update-message-status` | `{ messageId, status, senderId }` | Updates message status (e.g., `READ`, `DELIVERED`). |
| `add-reaction` | `{ messageId, reaction, isSenderReaction, targetRoom }` | Adds emoji reaction to a chat bubble. |
| `delete-message` | `{ messageId, isEveryone, targetRoom }` | Triggers single-user or global message deletions. |

### Server to Client Events (Listen)
| Event | Payload | Description |
| :--- | :--- | :--- |
| `user-presence-changed` | `{ userId, isOnline, lastSeen }` | Notified when any friend joins or disconnects. |
| `receive-message` | `Message` object | Triggered when a new direct/group message arrives. |
| `user-typing` | `{ senderId, receiverId, isGroup, isTyping }` | Controls visual "typing..." display. |
| `message-status-updated` | `{ messageId, status }` | Triggers visual tick marks updates (e.g. read receipts). |
| `reaction-added` | `{ messageId, reaction, isSenderReaction }` | Adds real-time reaction visual update to bubbles. |
| `message-deleted` | `{ messageId, isEveryone }` | Removes message bubble contents instantly on screen. |
| `group-updated` | `Group` object | Notifies details modified (e.g., name or banner). |
| `member-joined` | `{ groupId, userId }` | Notifies new user joined group via link. |
| `member-left` | `{ groupId, userId, kickedBy }` | Updates room member state on member eviction or leave. |

---

## 📱 Android Compilation & Build

### Running standard tasks
Verify code correctness and compile the Android app by running:
```bash
./gradlew assembleDebug
```

Enjoy using **i chat**! Feel free to modify the local companion server IP/URL in your client repository to connect real mobile devices or streaming emulators over your LAN!
