# ND Chat

ND Chat is a simple, peer-to-peer chat application for Android. It allows users to connect directly to each other and chat without the need for a central server.

## Building and Running

To build and run the app, you'll need Android Studio. You can clone the project and open it in Android Studio. From there, you can build the app and run it on an Android device or emulator.

## Project Structure

The project is organized into the following directories:

*   `app`: Contains the main application module.
*   `app/src`: Contains the app's source code, organized by feature.
*   `app/src/main/java/com/example/ndchat/model`: Contains the data models for the app.
*   `app/src/main/java/com/example/ndchat/ui_elements`: Contains the UI components for the app.
*   `app/src/main/java/com/example/ndchat/utils`: Contains utility functions.
*   `gradle`: Contains the Gradle wrapper files.

## How it Works

The app uses a peer-to-peer architecture, where each device acts as both a client and a server. When a user starts the app, they can either host a chat or join an existing one. When a user hosts a chat, their device starts a server on a specified port. When a user joins a chat, their device connects to the host's server.

Once a connection is established, users can send and receive messages. The app uses a simple messaging protocol to exchange messages between devices. The `PeerManager` class is responsible for managing the peer-to-peer connections and exchanging messages between devices.
