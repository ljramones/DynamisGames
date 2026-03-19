# Hello WorldEngine

The simplest possible Dynamis game. Validates the WorldEngine developer facade.

## What it does

- Initializes the engine
- Ticks for 120 frames (~2 seconds at 60Hz)
- Logs every 20 ticks
- Requests stop
- Shuts down cleanly

## The entire game

```java
public final class HelloWorldGame implements WorldApplication {

    @Override
    public void initialize(GameContext context) {
        System.out.println("[HelloWorld] Initialized!");
    }

    @Override
    public void update(GameContext context, float deltaSeconds) {
        if (context.tick() >= 120) {
            context.requestStop();
        }
    }

    @Override
    public void shutdown(GameContext context) {
        System.out.println("[HelloWorld] Shutdown complete.");
    }
}
```

## The entire launcher

```java
public static void main(String[] args) {
    WorldEngine.run(new HelloWorldGame());
}
```

## Build & Run

```bash
./build.sh
./run.sh
```
