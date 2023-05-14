package www.ontologyutils.toolbox;

import java.util.*;
import java.util.stream.Stream;

/**
 * Implementation of monte carlo tree search.
 */
public class Mcts<M> implements AutoCloseable {
    /**
     * Interface representing a game. Must be implemented by users of this class to
     * describe the search space.
     */
    public static interface Game<M> extends AutoCloseable {
        /**
         * @return A copy of the current game state.
         */
        public Game<M> copy();

        /**
         * @return The stream of possible moves that can be performed in the current
         *         state.
         */
        public Stream<M> possibleMoves();

        /**
         * @param move
         *            Move the current state based on the given move.
         */
        public void performMove(M move);

        /**
         * @return true if the current state is terminal, false otherwise.
         */
        public boolean isTerminal();

        /**
         * May only be called of {@code isTerminal} returns true.
         *
         * @return The value of the current state.
         */
        public double terminalValue();

        @Override
        public void close();
    }

    private class NodeStats {
        public int visitCount;
        public double valueSum;

        public double getValue() {
            if (visitCount != 0) {
                return valueSum / visitCount;
            } else {
                return 0;
            }
        }

        public void addVisit(double value) {
            visitCount += 1;
            valueSum += value;
        }

        public double getScaledValue() {
            if (minValue < maxValue) {
                return (getValue() - minValue) / (maxValue - minValue);
            } else {
                return 0;
            }
        }
    }

    private class Node extends NodeStats {
        public Map<M, Node> children;

        public boolean isExpanded() {
            return children != null && !children.isEmpty();
        }

        public void expand(Stream<M> moves) {
            if (!isExpanded()) {
                children = new HashMap<>();
                moves.forEach(move -> {
                    children.put(move, new Node());
                });
            }
        }

        public double raveContribution(M move, Node child) {
            if (useRave()) {
                var moveStats = getMoveStats(move);
                return ((double) moveStats.visitCount) / ((double) child.visitCount + (double) moveStats.visitCount
                        + 4 * raveBalance * raveBalance * (double) child.visitCount * (double) moveStats.visitCount);
            } else {
                return 0;
            }
        }

        public double getUcbScore(M move, Node child) {
            double countScore = expConstant;
            if (visitCount != 0) {
                countScore *= Math.sqrt(Math.log(visitCount) / child.visitCount);
            }
            double valueScore = child.getScaledValue();
            if (useRave()) {
                var moveStats = getMoveStats(move);
                var raveContr = raveContribution(move, child);
                valueScore *= (1 - raveContr);
                valueScore += raveContr * moveStats.getScaledValue();
            }
            return countScore + valueScore;
        }

        public Map.Entry<M, Node> selectChild() {
            return children.entrySet().stream()
                    .max(Comparator.comparingDouble(
                            e -> getUcbScore(e.getKey(), e.getValue())))
                    .get();
        }
    }

    private double expConstant;
    private int expThreshold;
    private double raveBalance;

    private Game<M> game;
    private Node root;
    private Map<M, NodeStats> moves;
    private double minValue;
    private double maxValue;

    /**
     * @param game
     *            The game to search.
     * @param expConstant
     *            The exploration constant. Higher means more exploration.
     * @param expThreshold
     *            The expansion threshold. Number of roll-outs before expanding a
     *            node.
     * @param raveBalance
     *            The balance factor for the RAVE heuristic. Use NaN to not use
     *            RAVE.
     */
    public Mcts(Game<M> game, double expConstant, int expThreshold, double raveBalance) {
        this.expConstant = expConstant;
        this.expThreshold = expThreshold;
        this.raveBalance = raveBalance;
        this.game = game;
        this.root = new Node();
        this.root.expand(game.possibleMoves());
        this.moves = new HashMap<>();
        this.minValue = Double.POSITIVE_INFINITY;
        this.maxValue = Double.NEGATIVE_INFINITY;
    }

    /**
     * @param game
     *            The game to search.
     */
    public Mcts(Game<M> game) {
        this(game, 1.5, 0, 1);
    }

    private boolean useRave() {
        return !Double.isNaN(raveBalance);
    }

    private NodeStats getMoveStats(M move) {
        return moves.computeIfAbsent(move, m -> new NodeStats());
    }

    private double rollout(Game<M> game) {
        while (!game.isTerminal()) {
            game.performMove(Utils.randomChoice(game.possibleMoves()));
        }
        return game.terminalValue();
    }

    private void backpropagate(Collection<Map.Entry<M, Node>> steps, double value) {
        if (value < minValue) {
            minValue = value;
        } else if (value > maxValue) {
            maxValue = value;
        }
        for (var step : steps) {
            if (useRave()) {
                getMoveStats(step.getKey()).addVisit(value);
            }
            step.getValue().addVisit(value);
        }
        root.addVisit(value);
    }

    /**
     * Run a single simulation iteration of selection, expansion, rollout and
     * backpropagation.
     */
    public void runSimulation() {
        try (var searchGame = game.copy()) {
            var searchPath = new ArrayList<Map.Entry<M, Node>>();
            var node = root;
            while (node.isExpanded()) {
                var actionNode = node.selectChild();
                node = actionNode.getValue();
                searchGame.performMove(actionNode.getKey());
                searchPath.add(actionNode);
            }
            if (node.visitCount >= expThreshold) {
                node.expand(searchGame.possibleMoves());
            }
            var value = rollout(searchGame);
            backpropagate(searchPath, value);
        }
    }

    /**
     * Perform the given move, both changing the current game state and root node.
     *
     * @param move
     *            The move to perform.
     */
    public void performMove(M move) {
        game.performMove(move);
        root = root.children.get(move);
        root.expand(game.possibleMoves());
    }

    /**
     * @return The current map from moves to visit count.
     */
    public Map<M, Integer> getRootCounts() {
        var result = new HashMap<M, Integer>();
        root.children.forEach((move, node) -> {
            result.put(move, node.visitCount);
        });
        return result;
    }

    /**
     * @return The current map from moves to node value.
     */
    public Map<M, Double> getRootValues() {
        var result = new HashMap<M, Double>();
        root.children.forEach((move, node) -> {
            result.put(move, node.getValue());
        });
        return result;
    }

    @Override
    public void close() {
        game.close();
    }
}
