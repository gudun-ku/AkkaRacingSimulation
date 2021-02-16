import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class RaceController extends AbstractBehavior<RaceController.Command> {

    public interface Command extends Serializable {}

    public static class StartCommand implements Command {
        public static long serialVersionUID = 3L;
    }

    public static class RacerUpdateCommand implements Command {
        private static final long serialVersionUID = 4L;

        private final ActorRef<Racer.Command> racer;
        private final int position;

        public RacerUpdateCommand(ActorRef<Racer.Command> racer, int position) {
            this.racer = racer;
            this.position = position;
        }

        public ActorRef<Racer.Command> getRacer() {
            return racer;
        }

        public int getPosition() {
            return position;
        }
    }

    // We don't need to call that command outside
    private class GetPositionsCommand implements Command {
        private static final long serialVersionUID = 5L;
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(RaceController::new);
    }

    private RaceController(ActorContext<Command> context) {
        super(context);
    }

    private Map<ActorRef<Racer.Command>, Integer> currentPositions;
    private long start;
    private int raceLength = 100;
    private Object TIMER_KEY;

    private void displayRace() {
        int displayLength = 160;

        for (int i = 0; i < 50; ++i) System.out.println();

        System.out.println("Race has been running for " + ((System.currentTimeMillis() - start) / 1000) + " seconds.");
        System.out.println("    " + new String (new char[displayLength]).replace('\0', '='));

        int i = 0;
        for (ActorRef<Racer.Command> racer: currentPositions.keySet()) {
            System.out.println(i + " : "  + new String (new char[currentPositions.get(racer) * displayLength / 100]).replace('\0', '*'));
            i++;
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartCommand.class, message -> {
                    start = System.currentTimeMillis();
                    currentPositions = new HashMap<>();
                    for (int i = 0; i < 10; i++) {
                        ActorRef<Racer.Command> racer = getContext().spawn(Racer.create(), "racer_" + i);
                        currentPositions.put(racer, 0);
                        racer.tell(new Racer.StartCommand(raceLength));
                    }
                    // Start the timer ticking every second
                    return Behaviors.withTimers(timer -> {
                        timer.startTimerAtFixedRate(TIMER_KEY, new GetPositionsCommand(), Duration.ofSeconds(1L));
                        return this;
                    });
                })
                .onMessage(GetPositionsCommand.class, message -> {
                    for (ActorRef<Racer.Command> racer: currentPositions.keySet()) {
                        racer.tell(new Racer.PositionCommand(getContext().getSelf()));
                    }
                    displayRace();
                    return this;
                })
                .onMessage(RacerUpdateCommand.class, message -> {
                    currentPositions.put(message.getRacer(), message.getPosition());
                    return this;
                })
                .build();
    }
}
