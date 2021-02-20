import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.Serializable;
import java.util.concurrent.ThreadLocalRandom;

public class Racer extends AbstractBehavior<Racer.Command> {

    public interface Command extends Serializable {}

    public static class StartCommand implements Command {
        private static final long serialVersionUID = 1L;

        private final int raceLength;

        public StartCommand(int raceLength) {
            this.raceLength = raceLength;
        }

        public int getRaceLength() {
            return raceLength;
        }
    }

    public static class PositionCommand implements Command {
        private static final long serialVersionUID = 2L;

        private final ActorRef<RaceController.Command> controller;

        public PositionCommand(ActorRef<RaceController.Command> controller) {
            this.controller = controller;
        }

        public ActorRef<RaceController.Command> getController() {
            return controller;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(Racer::new);
    }

    private final double defaultAverageSpeed = 48.2;

    // REMOVED STATE
    // private Random random;
    // private int averageSpeedAdjustmentFactor// private double currentSpeed = 0;
    // private double currentPosition = 0;
    // private int raceLength;

    private double getMaxSpeed(int averageSpeedAdjustmentFactor) {
        return defaultAverageSpeed * (1+((double)averageSpeedAdjustmentFactor / 100));
    }

    private double getDistanceMovedPerSecond(double currentSpeed) {
        return currentSpeed * 1000 / 3600;
    }

    private double determineNextSpeed(int raceLength, double currentSpeed, double currentPosition) {

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int averageSpeedAdjustmentFactor = random.nextInt(30) - 10;

        if (currentPosition < (raceLength / 4)) {
            currentSpeed = currentSpeed  + (((getMaxSpeed(averageSpeedAdjustmentFactor) - currentSpeed) / 10) * random.nextDouble());
        }
        else {
            currentSpeed = currentSpeed * (0.5 + random.nextDouble());
        }

        if (currentSpeed > getMaxSpeed(averageSpeedAdjustmentFactor))
            currentSpeed = getMaxSpeed(averageSpeedAdjustmentFactor);

        if (currentSpeed < 5)
            currentSpeed = 5;

        if (currentPosition > (raceLength / 2) && currentSpeed < getMaxSpeed(averageSpeedAdjustmentFactor) / 2) {
            currentSpeed = getMaxSpeed(averageSpeedAdjustmentFactor) / 2;
        }
        return currentSpeed;
    }

    private Racer(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return notYetStarted();
    }

    public Receive<Command> notYetStarted() {
        return newReceiveBuilder()
                .onMessage(StartCommand.class, command -> {
                    int raceLength = command.getRaceLength();
                    double nextSpeed = determineNextSpeed(raceLength, 0, 0);
                    return running(raceLength, nextSpeed, 0);
                })
                .onMessage(PositionCommand.class, message -> {
                    //tell the controller about current position
                    message.getController().tell(new RaceController.RacerUpdateCommand(getContext().getSelf(),0));
                    return Behaviors.same();
                })
                .build();
    }

    public Receive<Command> running(int raceLength, double currentSpeed, double currentPosition) {
        return newReceiveBuilder()
                .onMessage(PositionCommand.class, message -> {
                    double nextSpeed = determineNextSpeed(raceLength, currentSpeed, currentPosition);
                    double newPosition =  currentPosition + getDistanceMovedPerSecond(currentSpeed);
                    if (newPosition > raceLength ) {
                        newPosition  = raceLength;
                    }
                    //tell the controller about current position
                    message.getController().tell(new RaceController.RacerUpdateCommand(getContext().getSelf(),
                            (int) newPosition));

                    if (currentPosition == raceLength) {
                        return completed(raceLength);
                    } else {
                        return running(raceLength, nextSpeed, newPosition);
                    }
                })
                .build();
    }

    public Receive<Command> completed(int raceLength) {
        return newReceiveBuilder()
                .onMessage(PositionCommand.class, message -> {
                    //tell the controller about current position
                    message.getController().tell(new RaceController.RacerUpdateCommand(getContext().getSelf(),
                            raceLength));
                    return Behaviors.same();
                })
                .build();
    }

}
