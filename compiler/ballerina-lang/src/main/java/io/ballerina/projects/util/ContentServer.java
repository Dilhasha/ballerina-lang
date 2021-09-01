package io.ballerina.projects.util;

public class ContentServer {
    private Subscriber subscriber;

    private static ContentServer serverInstance;

    public static ContentServer getInstance() {
        if (serverInstance == null) {
            serverInstance = new ContentServer();
        }
        return serverInstance;
    }

    private ContentServer() {
    }

    public void sendMessage(String message) {
        subscriber.receivedMessage(message);
    }

    public void registerSubscriber(Subscriber s) {
        this.subscriber = s;
    }
}