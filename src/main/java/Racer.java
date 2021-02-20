import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.Serializable;
import java.util.Random;

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
    private Random random;
//    private int averageSpeedAdjustmentFactor;

//    private double currentSpeed = 0;
//    private double currentPosition = 0;
//    private int raceLength;

    private double getMaxSpeed(int averageSpeedAdjustmentFactor) {
        return defaultAverageSpeed * (1+((double)averageSpeedAdjustmentFactor / 100));
    }

    private double getDistanceMovedPerSecond(double currentSpeed) {
        return currentSpeed * 1000 / 3600;
    }

    private double determineNextSpeed(int raceLength, int averageSpeedAdjustmentFactor, double currentPosition) {
        double currentSpeed = 0;
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
                    random = new Random();
                    int raceLength = command.getRaceLength();
                    int averageSpeedAdjustmentFactor = random.nextInt(30) - 10;
                    return running(raceLength, averageSpeedAdjustmentFactor, 0);
                })

                .build();
    }

    public Receive<Command> running(int raceLength, int averageSpeedAdjustmentFactor, double currentPosition) {
        return newReceiveBuilder()
                .onMessage(PositionCommand.class, message -> {
                    double currentSpeed = determineNextSpeed(raceLength, averageSpeedAdjustmentFactor, currentPosition);
                    double newPosition =  currentPosition + getDistanceMovedPerSecond(currentSpeed);
                    if (newPosition > raceLength ) {
                        newPosition  = raceLength;
                        return completed(newPosition);
                    }

                    //tell the controller about current position
                    message.getController().tell(new RaceController.RacerUpdateCommand(getContext().getSelf(),
                            (int) newPosition));
                    return running(raceLength, averageSpeedAdjustmentFactor, newPosition);
                })
                .build();
    }

    public Receive<Command> completed(double currentPosition) {
        return newReceiveBuilder()
                .onMessage(PositionCommand.class, message -> {
                    //tell the controller about current position
                    message.getController().tell(new RaceController.RacerUpdateCommand(getContext().getSelf(),
                            (int) currentPosition));
                    return this;
                })
                .build();
    }

}
