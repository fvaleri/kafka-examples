package it.fvaleri.kafka;

public class Main {
    public static void main(String[] args) {
        try {
            switch (Configuration.get().clientType()) {
                case null -> { System.err.println("Empty client type"); System.exit(1); }
                case "producer" -> new Producer("producer-thread").start();
                case "consumer" -> new Consumer("consumer-thread").start();
                default -> { System.err.println("Unknown client type"); System.exit(1); }
            }
        } catch (Throwable e) {
            System.err.println("Unhandled exception");
            e.printStackTrace();
        }
    }
}
