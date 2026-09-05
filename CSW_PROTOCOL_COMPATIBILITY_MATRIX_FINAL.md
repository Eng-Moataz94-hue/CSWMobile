# CSW Protocol Compatibility Matrix — Final Review
**Document:** `CSW_PROTOCOL_COMPATIBILITY_MATRIX_FINAL.md`  
**Target System:** Collaborative Student Workspace (CSW)  
**Clients:** CSWMobile (Android Native) & ChatClient (Windows Desktop)  
**Server:** ChatServer (C# / .NET)  
**Date:** September 5, 2026  
**Status:** Official Engineering Deliverable — Final Verified Audit  

---

## 1. Executive Summary

This document provides a protocol-compatibility and implementation-status audit of the CSWMobile Android client based on the Android project files and the architectural reports available to the reviewer.

The audit does not claim that the Android client is fully compatible with ChatServer. The Android application has been implemented and compiled, but cross-platform compatibility remains unverified because the authoritative C# protocol implementation was not available for direct inspection and no live integration test was executed against a running ChatServer instance.

### Android Implementation Status

The Android project contains:

- Kotlin source files for protocol models and packet handling.
- A coroutine-based TCP socket manager.
- Jetpack Compose user interfaces.
- ViewModel-based connection and workspace state management.
- Preliminary authentication, messaging, collaboration, and file-transfer flows.

These components demonstrate that an Android client prototype has been implemented. They do not, by themselves, prove that the client can communicate successfully with the C# server.

### Build Verification

The Android project was reported as compiling successfully using the Gradle configuration present in the project.

The exact Gradle, Android Gradle Plugin, Kotlin, and Compose versions are not independently asserted in this document unless they are directly confirmed from the project configuration files.

### Network and Integration Verification

No live end-to-end integration test has been performed against an active ChatServer instance.

The following items therefore remain unverified:

- TCP port and listener configuration.
- Packet header structure.
- Message type numeric values.
- Authentication sequence.
- JSON field names and data types.
- Snapshot synchronization.
- File-transfer framing.
- SignalR routes and hub methods.
- Cross-platform message delivery.

### Official Verdict

> **Build Successful — Protocol Compatibility Not Verified**

The current Android implementation should be classified as a partially implemented client prototype pending authoritative protocol verification and empirical LAN testing.

---

## 2. Evidence and Source Limitations

This audit is strictly bounded by the following evidential parameters:
1. **Source Scope:** Analysis is based exclusively on the current Android codebase (`com.example.csw`), build configuration files, resource files, and the prior architectural audit reports (`CSWMobile_PROJECT_EXPLANATION.md` and `CSWMobile_IMPLEMENTATION_REVIEW.md`).
2. **C# Source Unavailability:** The original upstream C# repository files (`ChatServer.cs`, `ChatProtocol.cs`, `ServerForm.cs`, `CallHub.cs`, `CollaborationHub.cs`) were not directly uploaded or parsed in this environment.
3. **Absence of Live Integration Telemetry:** No packet captures (e.g., Wireshark/pcap) or server runtime console logs were available to verify runtime framing.
4. **Epistemic Classification Standard:** Every assertion in this document is explicitly classified as `[Confirmed]`, `[Confirmed in Android]`, `[Requires C# Source]`, `[Engineering Assumption]`, or `[Not Present]`.

---

## 3. Current Android Implementation Audit

Eight primary Kotlin source files were reviewed, in addition to Android configuration and resource files, as documented below:

| Component / File Path | Architectural Role | Key Functions / Methods | Caller / Invoked By | Dependencies / Callee | Implementation Completeness | Integration Test Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `/app/src/main/java/com/example/csw/model/MessageType.kt` | Protocol Opcode Enum | `fromCode(code: Int)` | `ChatProtocol.kt`, `TcpSocketManager.kt`, `CSWViewModel.kt` | None | Fully Implemented (14 enum constants) | `[Not Verified]` (Opcodes inferred) |
| `/app/src/main/java/com/example/csw/model/CollaborationModels.kt` | Domain Models & DTOs | `toJson()`, `fromJson()` for all DTOs | `CSWViewModel.kt`, `WorkspaceScreen.kt` | `org.json.JSONObject`, `org.json.JSONArray` | Fully Implemented | `[Not Verified]` (Schema inferred) |
| `/app/src/main/java/com/example/csw/protocol/ChatProtocol.kt` | Binary Packet Framing | `readPacket()`, `writePacket()`, `readFully()` | `TcpSocketManager.kt` | `MessageType.kt`, `ByteBuffer`, `ByteOrder` | Fully Implemented with buffer protection | `[Not Verified]` (Framing inferred) |
| `/app/src/main/java/com/example/csw/network/TcpSocketManager.kt` | Asynchronous TCP Engine | `connect()`, `sendPacket()`, `sendRawPacket()`, `startReaderLoop()`, `disconnect()` | `CSWViewModel.kt` | `ChatProtocol.kt`, `Socket`, `Dispatchers.IO`, `Mutex` | Fully Implemented (Thread-safe) | `[Not Verified]` (No live socket test) |
| `/app/src/main/java/com/example/csw/viewmodel/CSWViewModel.kt` | MVVM State Coordinator | `connect()`, `performAuthHandshake()`, `handleIncomingPacket()`, `sendChatMessage()`, `sendFile()`, `onUserTyping()`, `disconnect()` | `MainActivity.kt`, Compose Screens | `TcpSocketManager`, `CollaborationModels`, `StateFlow` | Fully Implemented | `[Not Verified]` (Live flow unverified) |
| `/app/src/main/java/com/example/csw/ui/screens/ConnectionScreen.kt` | Connection Setup UI | `ConnectionScreen()` | `MainActivity.kt` | Compose Material 3, ViewModel events | Fully Functional UI | `[Confirmed in Android]` (Local UI only) |
| `/app/src/main/java/com/example/csw/ui/screens/WorkspaceScreen.kt` | Multi-tab Workspace UI | `WorkspaceScreen()`, `ChatView()`, `FilesView()`, `MembersView()`, `AcademicInfoView()` | `MainActivity.kt` | Compose Material 3, Coil `AsyncImage` | Fully Functional UI (File download is placeholder) | `[Confirmed in Android]` (Local UI only) |
| `/app/src/main/java/com/example/MainActivity.kt` | Activity Entry Point | `onCreate()` | Android OS | `CSWViewModel`, `Crossfade`, Compose Surface | Fully Implemented | `[Confirmed in Android]` (Passes compilation) |
| `/app/src/main/AndroidManifest.xml` | App Manifest & Permissions | Manifest Configuration | Android OS / Gradle | Platform permissions | Declares `INTERNET`, `ACCESS_NETWORK_STATE`, `usesCleartextTraffic` | `[Confirmed in Android]` |
| `/app/build.gradle.kts` | Build Configuration | Gradle Build Script | Gradle / AGP | Project dependencies | Validated & Building | `[Confirmed in Android]` |

---

## 4. Protocol Header Analysis

The binary packet header constitutes the primary failure domain in cross-platform TCP socket interoperability.

```text
       0                   1                   2                   3
       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |               MessageType Code (4 Bytes, Int32)               |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |               PayloadLength (4 Bytes, Int32)                  |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                                                               |
      +                       Payload (N Bytes)                       +
      |              (UTF-8 Encoded JSON or Raw Binary)               |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Detailed Framing Audit

| Parameter | Android Current Behavior | Expected C# Behavior | Confidence Level | Required Verification Item |
| :--- | :--- | :--- | :--- | :--- |
| **Total Header Size** | 8 bytes (`HEADER_SIZE = 8`) | Unknown until C# header parser is examined | `[Engineering Assumption]` | Verify field declarations in the C# protocol implementation. |
| **MessageType Field** | 4 bytes, 32-bit signed integer | Unknown (could be `int`, `short`, `byte`, or enum) | `[Requires C# Source]` | Check underlying enum type in C# protocol code. |
| **PayloadLength Field**| 4 bytes, 32-bit signed integer | Unknown (could be 7-bit string length, `int`, or `long`) | `[Requires C# Source]` | Check whether server uses `BinaryWriter.Write(int)` or `BinaryWriter.Write(string)`. |
| **Endianness** | Little-Endian (`ByteOrder.LITTLE_ENDIAN`) | Little-Endian is an engineering assumption based on common .NET binary serialization behavior. The actual server implementation must be inspected before confirming it | `[Engineering Assumption]` | Confirm whether custom byte swapping (`BitConverter` / `BinaryPrimitives`) is present in C#. |
| **Fragmentation Assembly**| `readFully()` loops until all `length` bytes arrive | Socket reader loop | `[Confirmed in Android]` | Verify that packet assembly correctly handles fragmented and coalesced TCP reads independently of network segment boundaries. |
| **Buffer Overflow Guard**| Capped at 50 MB (`MAX_PAYLOAD_SIZE`) | Unknown server threshold | `[Confirmed in Android]` | Check server maximum packet limit. |
| **Negative Length Guard**| Throws `IllegalArgumentException` if length < 0 | Unknown | `[Confirmed in Android]` | Ensure server handles zero-length payloads gracefully. |
| **EOF / Disconnect Detection**| Detects EOF when the input stream returns -1 | Catches socket disconnect / EOF | `[Confirmed in Android]` | Verify clean teardown and exception handling on both sides. |

---

## 5. MessageType Compatibility Matrix

The following table contrasts the opcodes defined in Android's `MessageType.kt` against the upstream server:

| Kotlin Enum Identifier | Kotlin Opcode Value | Expected C# Identifier | C# Opcode Value | Status | Evidential Basis |
| :--- | :---: | :--- | :---: | :--- | :--- |
| `UNKNOWN` | 0 | None / Undefined | Unknown | `[Engineering Assumption]` | Defensive fallback in Android only. |
| `AUTH_REQUEST` | 1 | `Auth` / `AuthRequest` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `AUTH_SUCCESS` | 2 | `AuthSuccess` / `LoginSuccess` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `AUTH_FAILED` | 3 | `AuthFailed` / `LoginFailed` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `CHAT_MESSAGE` | 4 | `ChatMessage` / `Message` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `SEND_FILE` | 5 | `SendFile` / `FileTransfer` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `FILE_DATA_CHUNK` | 6 | `FileData` / `FileChunk` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `FILE_ACK` | 7 | `FileAck` / `FileReceived` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `COLLABORATION_SNAPSHOT` | 8 | `CollaborationSnapshot` / `Snapshot` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `COMMAND` | 9 | `Command` / `CollaborationCommand` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `PRESENCE_UPDATE` | 10 | `PresenceUpdate` / `UserStatus` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `TYPING_INDICATOR` | 11 | `Typing` / `TypingIndicator` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `DISCONNECT` | 12 | `Disconnect` / `Leave` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |
| `HEARTBEAT` | 13 | `Heartbeat` / `Ping` | Unknown | `[Requires C# Source]` | Not verifiable without MessageType definition in the available C# protocol implementation. |

> **Critical Note:** None of the integer opcode assignments (1 through 13) can be certified as compatible without direct inspection of the `MessageType` definition in the available C# protocol implementation.

---

## 6. Authentication Compatibility Analysis

### Current Android Handshake Flow
1. **User Input:** User inputs Server IPv4, TCP Port (default: 5000), Username, and optional Password via `ConnectionScreen`.
2. **Socket Connect:** `TcpSocketManager.connect(ip, port)` initiates asynchronous TCP socket to `InetSocketAddress`.
3. **Transmission:** Upon socket connection, `performAuthHandshake()` immediately transmits an `AuthPayload` packet with opcode `AUTH_REQUEST` (Code 1).
4. **State Transition:** `CSWViewModel` awaits an incoming packet with opcode `AUTH_SUCCESS` (Code 2) or `COLLABORATION_SNAPSHOT` (Code 8).

### Handshake Compatibility Audit

| Verification Checkpoint | Android Implementation | C# Status | Empirical Result |
| :--- | :--- | :--- | :--- |
| **Socket Connection** | Native `Socket.connect()` | Native `TcpListener.AcceptTcpClientAsync()` | `[Theoretically Compatible]` |
| **Cleartext Support** | `usesCleartextTraffic="true"` enabled | Unencrypted TCP socket listener | `[Confirmed in Android]` |
| **Auth Initiation Order** | Client sends first | Unknown (Does server send banner first?) | `[Requires C# Source]` |
| **Payload Format** | UTF-8 JSON | Unknown (Binary DTO or JSON string) | `[Requires C# Source]` |
| **Auth Response Handling** | Expects Opcode 2 or 8 | Unknown (Sends ACK or raw Snapshot) | `[Requires C# Source]` |
| **Live Handshake Execution** | Never executed against server | Not run | `[Not Verified]` |

### Authentication Status Verdict
> **Authentication Not Verified**

---

## 7. JSON Model Compatibility Matrix

To mitigate serialization casing mismatches between .NET (`System.Text.Json` / `Newtonsoft.Json`) and Android (`org.json`), the Android implementation was defensively programmed to emit and parse both `PascalCase` and `camelCase` keys. However, structural discrepancies remain unverified:

| Model | Android Fields Implemented | Expected C# Fields | Casing Strategy | Types (Kotlin vs C#) | Nullability | Compatibility Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`AuthPayload`** | `username`, `password`, `clientType`, `deviceName` | `Username`, `Password` | Dual (`PascalCase` & `camelCase`) | `String` vs `string` | Non-null defaults | `[Requires C# Source]` (C# field mapping cannot be confirmed without server source) |
| **`SendMessagePayload`**| `messageId`, `senderUsername`, `content`, `timestamp`, `messageType` | `SenderUsername`, `Content` | Dual (`PascalCase` & `camelCase`) | `String` vs `string` (UUID vs `Guid`) | Defaults provided | `[Requires C# Source]` (C# field mapping cannot be confirmed without server source) |
| **`CollaborationSnapshot`**| `connectedUsers`, `recentMessages`, `sharedFiles`, `serverTime` | `ConnectedUsers`, `Messages` | Dual (`PascalCase` & `camelCase`) | Lists of Objects | Null-safe empty lists | `[Requires C# Source]` (C# field mapping cannot be confirmed without server source) |
| **`ConnectedUser`** | `username`, `isOnline`, `status`, `clientType`, `ipAddress` | `Username`, `IsOnline` | Dual (`PascalCase` & `camelCase`) | `String`, `Boolean` | Safe fallbacks | `[Requires C# Source]` (C# field mapping cannot be confirmed without server source) |
| **`FileItem`** | `id`, `fileName`, `fileSize`, `sender`, `timestamp`, `isImage` | `FileName`, `FileSize` | Dual (`PascalCase` & `camelCase`) | `String`, `Long`, `Boolean` | Safe fallbacks | `[Requires C# Source]` (C# field mapping cannot be confirmed without server source) |
| **`FileMetadataPayload`**| `fileId`, `fileName`, `fileSize`, `sender`, `isImage` | `FileId`, `FileName`, `FileSize` | Dual (`PascalCase` & `camelCase`) | `String`, `Long`, `Boolean` | Safe fallbacks | `[Requires C# Source]` (C# field mapping cannot be confirmed without server source) |
| **`CollaborationCommand`**| `commandType`, `username`, `extraData` | `CommandType`, `Username` | Dual (`PascalCase` & `camelCase`) | `String` | Safe fallbacks | `[Requires C# Source]` (C# field mapping cannot be confirmed without server source) |

---

## 8. Authentication and Message Flow

The operational flow implemented across the Android layers is mapped below, highlighting confirmed versus assumed stages:

```text
[User Action: Tap Connect]
       │
       ▼ [Confirmed in Android]
Android UI (ConnectionScreen)
       │
       ▼ [Confirmed in Android]
CSWViewModel.connect()
       │
       ▼ [Confirmed in Android]
TcpSocketManager.connect(ip, port)
       │
       ▼ [Theoretically Compatible / Requires Same Subnet]
TCP Socket (java.net.Socket.connect via Dispatchers.IO)
       │
       ▼ [Not Verified with Server]
CSW Server (ChatServer TCP Listener)
       │
       ├─► [Assumption: Server accepts handshake without preliminary banner]
       │
       ▼ [Confirmed in Android / Opcode Unverified in C#]
ChatProtocol.writePacket(AUTH_REQUEST, AuthPayload.toJson())
       │
       ▼ [Not Verified with Server]
Server parses packet
       │
       ├─► IF Valid: Server replies with AUTH_SUCCESS (Opcode 2) OR Snapshot (Opcode 8)
       │             [Requires C# Source to verify exact sequence]
       │
       ▼ [Confirmed in Android]
TcpSocketManager reader loop receives packet
       │
       ▼ [Confirmed in Android]
ChatProtocol.readPacket() extracts Opcode & Payload
       │
       ▼ [Confirmed in Android]
CSWViewModel updates WorkspaceUiState.isLoggedIn = true
       │
       ▼ [Confirmed in Android]
MainActivity transitions UI via Crossfade to WorkspaceScreen
```

---

## 9. File and Media Transfer Compatibility

### Android Implementation Details
1. **File Selection:** Implemented using Android's zero-permission `ActivityResultContracts.GetContent()`.
2. **Metadata Formulation:** Extracts display name and byte size via `OpenableColumns`. Emits `FileMetadataPayload` with `SEND_FILE` opcode (Code 5).
3. **Payload Transmission:** Reads entire file stream into memory via `readBytes()` and transmits raw bytes through `sendRawPacket(FILE_DATA_CHUNK, bytes)`.
4. **Local Rendering:** Adds optimistic entry into `files` list and chat stream. If mime type matches an image, renders via Coil `AsyncImage`.

### Server Compatibility Analysis
* **Mechanism Alignment:** There is **no proof** that `ChatServer.cs` expects file transfers as a metadata JSON packet followed immediately by raw binary data on the primary socket.
* **Alternative Possibilities in C#:**
  - Base64 encoding embedded directly inside `SendMessagePayload.content`.
  - Multipart chunked frames containing an internal chunk header (`ChunkIndex`, `TotalChunks`, `ChunkData`).
  - Separate dedicated file streaming TCP port or HTTP multipart upload endpoint.
* **Status Verdict:**
  > **File transfer compatibility is not verified.**

---

## 10. SignalR Compatibility

| Question | Assessment | Evidential Basis |
| :--- | :--- | :--- |
| **Is SignalR client code present in Android?** | **No [Not Present]** | Zero SignalR dependencies or connection classes exist in the Android module. |
| **Is the Hub URL or Route confirmed?** | **No [Requires C# Source]** | Routes such as `/collaborationHub` or `/chatHub` are unverified. |
| **Are Hub Methods or Events confirmed?** | **No [Requires C# Source]** | Client/Server RPC methods are completely unverified. |
| **Has SignalR integration been tested?** | **No [Not Verified]** | No runtime calls executed. |

> **Official Status:**  
> **SignalR client implementation: Not present**

---

## 11. Video Calls and Optical Character Recognition (OCR)

| Capability | Current State in Android | State in Upstream C# | Academic & Technical Reality |
| :--- | :--- | :--- | :--- |
| **Local Video Recording** | Not implemented | Reported in prior architectural notes as a desktop component; not independently verified in this audit | Purely local camera recording to an `.mp4` file. Not a network stream. |
| **Live Video Calling** | Not implemented | Previous architectural notes describe CallHub as a signaling-related component; its actual implementation was not independently verified because the C# source was unavailable | No WebRTC engine, RTP stack, or Media Server exists in either codebase. |
| **Live Audio Calling** | Not implemented | Audio capture stubs only | No live audio transport exists between mobile and server. |
| **Optical Character Recognition (OCR)** | Not implemented | Not present in server or desktop codebase | Purely hypothetical feature. Zero OCR libraries exist. |

> **Academic Defense Guidance:** Students must explicitly clarify to examiners that live video streaming and OCR do not exist in the project scope. Video recording refers strictly to local webcam capture on Windows.

---

## 12. Master Compatibility Matrix

The following comprehensive matrix classifies every system capability across both runtimes:

| System Feature | Android Status | C# Evidence Available | Integration Tested | Final Compatibility Classification |
| :--- | :--- | :--- | :--- | :--- |
| **TCP Socket Engine** | Fully implemented (`TcpSocketManager`) | `TcpListener` confirmed in architecture | No | `[Partially Implemented — Requires Integration Test]` |
| **Cleartext LAN Access**| Configured (`usesCleartextTraffic`) | Unencrypted socket | No | `[Confirmed in Android]` |
| **Protocol Header Framing**| 8 bytes Little-Endian | Architecture notes only | No | `[Requires C# Source]` |
| **MessageType Opcodes** | 14 opcodes defined in Kotlin | Inferred from reports | No | `[Requires C# Source]` |
| **User Authentication** | Handshake implemented in ViewModel | Inferred from reports | No | `[Not Verified]` |
| **Snapshot Synchronization**| Parsing logic implemented | Inferred from reports | No | `[Requires C# Source]` |
| **Text Chat Messaging** | Implemented with M3 UI bubbles | Inferred from reports | No | `[Partially Implemented — Requires C# Source]` |
| **File / Image Upload** | Implemented via ContentResolver | Inferred from reports | No | `[Not Verified]` |
| **File Download Action** | Placeholder UI only | Inferred from reports | No | `[Not Implemented]` |
| **User Presence Updates** | Model and UI indicators ready | Inferred from reports | No | `[Not Verified]` |
| **Typing Indicators** | Implemented in ViewModel & UI | Inferred from reports | No | `[Not Verified]` |
| **SignalR Real-time Hub** | Not present in Android | Present in C# solution | No | `[Not Implemented]` |
| **Live Video / Audio Call**| Not present | Signaling stubs only | No | `[Not Implemented]` |
| **OCR Functionality** | Not present | Not present | No | `[Not Present]` |

---

## 13. BLOCKERS

The following blockers currently prevent certifying end-to-end compatibility:

1. **[BLOCKER 1] Absence of Upstream C# Source Code:** Inability to inspect the C# protocol and server implementation prevents definitive validation of opcodes, header length, and endianness.
2. **[BLOCKER 2] Unverified Opcode Mapping:** If the server assigns different integers to message types (e.g., `Auth = 0` or `Auth = 100`), the handshake will fail immediately upon connection.
3. **[BLOCKER 3] Unverified Header Structure:** If the server uses single-byte opcodes or string-prefixed length fields (e.g., `BinaryWriter.Write(String)` 7-bit encoded integers), the Android framing parser will experience immediate frame boundary desynchronization.
4. **[BLOCKER 4] Unverified File Protocol:** The lack of specification for binary file streams prevents confirming whether file uploads will be received or cause socket disconnections.
5. **[BLOCKER 5] Lack of Physical LAN Testing:** The system has not been tested over an active Wi-Fi router connecting an Android device to a Windows host executing `ChatServer.exe`.

---

## 14. REQUIRED CHANGES

The following tasks outline the exact work required once the blockers are addressed:

### Required After C# Protocol Verification
* [ ] Align integer constants in `MessageType.kt` with the official enum in the C# protocol code.
* [ ] Adjust `ChatProtocol.kt` header reading logic if header size, field ordering, or endianness differs from 8 bytes Little-Endian.
* [ ] Align JSON DTO field names in `CollaborationModels.kt` with the exact serialization attributes used in C#.
* [ ] Update `performAuthHandshake()` in `CSWViewModel.kt` if the server mandates a preliminary greeting message before client authentication.

### Required After First Integration Test
* [ ] Validate socket timeout and keep-alive intervals under variable Wi-Fi latency.
* [ ] Verify file chunking boundaries and buffer allocation during large asset transfers.
* [ ] Implement server file retrieval handler (downloading files from server to Android local storage).
* [ ] Implement exponential backoff reconnection logic in `TcpSocketManager.kt`.

### Optional Future Features
* [ ] Add official ASP.NET Core SignalR Java client library if real-time notifications are migrated away from raw TCP.
* [ ] Implement WebRTC media stack if live audio/video calls are added to the server.

---

## 15. Empirical Integration Test Plan

The following test suite must be executed in a physical or local lab environment prior to final presentation:

| Test ID | Test Objective | Test Inputs | Expected Result | Success Criteria | Execution Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **IT-01** | LAN Socket Reachability | Host IPv4, Port 5000 | TCP SYN-ACK received | `SocketConnectionState.Connected` emitted | `[Not Executed]` |
| **IT-02** | Firewall Traversal | Windows Firewall rule | Inbound packet allowed | Connection succeeds without timeout | `[Not Executed]` |
| **IT-03** | Auth Packet Serialization | Username: "TestStudent" | 8-byte header + UTF-8 JSON | Server logs show client login request | `[Not Executed]` |
| **IT-04** | Auth Response Handling | Server response packet | Opcode 2 or 8 received | UI transitions to `WorkspaceScreen` | `[Not Executed]` |
| **IT-05** | Snapshot Hydration | Snapshot JSON payload | Deserialized lists populated | Messages and members render in UI tabs | `[Not Executed]` |
| **IT-06** | Text Message Outbound | "Hello from Android" | Opcode 4 transmitted | Message appears on Windows desktop client | `[Not Executed]` |
| **IT-07** | Text Message Inbound | Message sent from PC | Opcode 4 received | Bubble appears in Android LazyColumn | `[Not Executed]` |
| **IT-08** | Presence Synchronization| Client disconnects | Opcode 10 or socket EOF | Member status indicator switches to offline | `[Not Executed]` |
| **IT-09** | Small Image Upload (<2MB)| Select `.png` via Picker | Opcode 5 + Raw bytes | Image file saved on server storage | `[Not Executed]` |
| **IT-10** | Large File Transfer (>10MB)| Select `.pdf` via Picker | Stream transmission | Complete file checksum matches on server | `[Not Executed]` |
| **IT-11** | Socket Teardown | Tap Disconnect button | Socket close, reader cancel | Clean disconnect without server crash | `[Not Executed]` |
| **IT-12** | Abrupt Disconnect Recovery| Toggle Android Wi-Fi Off | IOException caught | Error banner displayed; retry permitted | `[Not Executed]` |

---

## 16. Final Engineering Verdict

Based strictly on the Android implementation evidence available in the repository, the reported successful build, and the absence of authoritative C# protocol inspection and live integration testing, the current status is:

```text
================================================================================
                              FINAL VERDICT
================================================================================

  [ ] Not Compatible
  [X] Partially Implemented — Compatibility Not Verified
  [ ] Theoretically Compatible
  [ ] Actually Compatible — Fully Verified

  RATIONALE:

  The Android client has been implemented and the project build succeeds
  according to the available Android project evidence. However, cross-platform
  compatibility with the existing C# server has not been verified because the
  original C# source implementation, exact MessageType values, binary header
  format, authentication sequence, JSON contract, and file-transfer protocol
  were not directly inspected or tested through a real LAN integration test.
================================================================================
```

---

## 17. NEXT REQUIRED ACTION

Prior to presenting or releasing the system for academic defense, the following sequential verification actions must be performed:

1. **Run the Original ChatServer:** Launch `ChatServer.exe` on the Windows host machine in the target local area network.
2. **Identify Server IPv4 Address:** Execute `ipconfig` in the Windows command prompt to extract the actual host IPv4 address (e.g., `192.168.1.X`).
3. **Verify the Active TCP Port:** Confirm the listening port configured in the server UI or settings (e.g., Port 5000) and verify that Windows Firewall allows inbound TCP traffic on that port.
4. **Connect Mobile Device to Same Wi-Fi:** Ensure the Android device (or streaming emulator) is connected to the identical local subnet and can ping the host IP.
5. **Execute Empirical Integration Tests:** Conduct a live connection, authentication handshake, text message exchange, and file transmission test.
6. **Record Real Telemetry:** Document actual packet captures and server log entries to replace all theoretical assumptions with verified empirical data.

---
*Report generated and saved to workspace root.*  
*File Name:* `CSW_PROTOCOL_COMPATIBILITY_MATRIX_FINAL.md`
