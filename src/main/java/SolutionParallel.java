import java.io.IOException;
import java.util.*;

public class SolutionParallel extends Solution {
    private TaskGraph taskGraph;
    private int numProcessors;
    private int numTasks;

    // does not change
    private int[] nodePriorities;   //REFACTORRRRRRRRRRRRRRRRR
    private ArrayList<Integer>[] equivalentNodesList;  //REFACTORRRRRRRRRRRRRRRRR
    private int[] maxLengthToExitNode;

    private int[] bestStartTime; // bestStartTime[i] => start time of task i in best schedule found so far
    private int[] bestScheduledOn; // bestScheduledOn[i] => processor that task i is scheduled on, in best schedule
    private int bestFinishTime; // earliest finishing time of schedules we have searched
    HashSet<Integer> seenSchedules = new HashSet<>();

    /**
     * Creates an optimal scheduling of tasks on specified number of processors.
     *
     * @param taskGraph      Graph containing tasks as nodes and their dependencies as edges.
     * @param numProcessors  Number of processors to schedule the tasks on.
     * @param upperBoundTime Upper bound of running time that the optimal solution should do at least as good as.
     * @return optimal schedule found by the run method.
     */
    public Schedule run(TaskGraph taskGraph, int numProcessors, int upperBoundTime) throws IOException, ClassNotFoundException {
        initializeGlobalVars(taskGraph, numProcessors, upperBoundTime);
        State initialState = initializeState(taskGraph, numProcessors);

        recursiveSearch(initialState);
        return createOutput();
    }

    /**
     * Recursively try to schedule a task on a processor.
     * Uses DFS to try all possible schedules.
     */
    private void recursiveSearch(State state) throws IOException, ClassNotFoundException {
        // Base case is when queue is empty, i.e. all tasks scheduled.
        if (state.candidateTasks.isEmpty()) {
            int finishTime = findMaxInArray(state.processorFinishTimes);

            //If schedule time is better, update bestFinishTime and best schedule
            if (finishTime < bestFinishTime) {
                bestFinishTime = finishTime;

                for (int i = 0; i < bestStartTime.length; i++) {
                    bestScheduledOn[i] = state.scheduledOn[i];
                    bestStartTime[i] = state.taskStartTimes[i];
                }
            }
            return;
        }

        // Create a hash code for our partial schedule to check whether we have examined an equivalent schedule before
        // If we have seen an equivalent schedule we do not need to proceed
        int hashCode = PartialSchedule.generateHashCode(state.taskStartTimes, state.scheduledOn, numProcessors);
        if (seenSchedules.contains(hashCode)) {
            return;
        } else {
            seenSchedules.add(hashCode);
        }

        // Information we need about the current schedule
        // minimal remaining time IF all remaining tasks are evenly distributed amongst processors.
        int loadBalancedRemainingTime = (int) Math.ceil(state.remainingDuration / (double) numProcessors);

        int earliestProcessorFinishTime = Integer.MAX_VALUE;
        int latestProcessorFinishTime = 0;
        for (int l = 0; l < numProcessors; l++) {
            earliestProcessorFinishTime = Math.min(state.processorFinishTimes[l], earliestProcessorFinishTime);
            latestProcessorFinishTime = Math.max(state.processorFinishTimes[l], latestProcessorFinishTime);
        }

        int longestCriticalPath = 0;
        for(int task : state.candidateTasks){
            int criticalPath = maxLengthToExitNode[task];
            if (criticalPath > longestCriticalPath){
                longestCriticalPath = criticalPath;
            }
        }

        // Iterate through tasks
        state.candidateTasks.sort(Comparator.comparingInt(a -> nodePriorities[a]));
        HashSet<Integer> seenTasks = new HashSet<>();
        for (int i = 0; i < state.candidateTasks.size(); i++) {
            int candidateTask = state.candidateTasks.remove();
            if(seenTasks.contains(candidateTask)){
                state.candidateTasks.add(candidateTask);
                continue;
            } else {
                ArrayList<Integer> equivalentNodes = equivalentNodesList[candidateTask];
                seenTasks.addAll(equivalentNodes);
            }

            // Exit conditions 1
            boolean loadBalancingConstraint = earliestProcessorFinishTime + loadBalancedRemainingTime >= bestFinishTime;
            boolean criticalPathConstraint = earliestProcessorFinishTime + longestCriticalPath >= bestFinishTime;
            boolean latestFinishTimeConstraint = latestProcessorFinishTime >= bestFinishTime;
            if (loadBalancingConstraint || criticalPathConstraint || latestFinishTimeConstraint) {
                state.candidateTasks.add(candidateTask);
                continue;
            }

            // Update state (Location 1: Candidate Task)
            state.remainingDuration -= taskGraph.getDuration(candidateTask);
            List<Integer> candidateChildren = taskGraph.getChildrenList(candidateTask);
            for (Integer candidateChild : candidateChildren) {
                state.inDegrees[candidateChild]--;
                if (state.inDegrees[candidateChild] == 0) {
                    state.candidateTasks.add(candidateChild);
                }
            }

            // Calculate information we need about constraints due to communication costs
            int maxDataArrival = 0;
            int processorCausingMaxDataArrival = 0;
            int secondMaxDataArrival = 0;
            List<Integer> parents = taskGraph.getParentsList(candidateTask);
            for (int parent : parents) {
                int dataArrival = state.taskStartTimes[parent] + taskGraph.getDuration(parent) + taskGraph.getCommCost(parent, candidateTask);
                if (dataArrival >= maxDataArrival) {
                    if (state.scheduledOn[parent] != processorCausingMaxDataArrival) {
                        secondMaxDataArrival = maxDataArrival;
                    }
                    maxDataArrival = dataArrival;
                    processorCausingMaxDataArrival = state.scheduledOn[parent];

                } else if (dataArrival >= secondMaxDataArrival) {
                    if (state.scheduledOn[parent] != processorCausingMaxDataArrival) {
                        secondMaxDataArrival = dataArrival;
                    }
                }
            }


            // Deep copy of candidateList is used in next recursive iteration
            boolean hasBeenScheduledAtStart = false;
            for (int candidateProcessor = 0; candidateProcessor < numProcessors; candidateProcessor++) { // Iterate through processors
                // Avoid processor isomorphism
                if (state.processorFinishTimes[candidateProcessor] == 0) {
                    if (hasBeenScheduledAtStart) {
                        // Skip duplicated search space
                        continue;
                    } else {
                        hasBeenScheduledAtStart = true;
                    }
                }

                // Find earliest time to schedule candidate task on candidate processor
                int earliestStartTimeOnCurrentProcessor = state.processorFinishTimes[candidateProcessor];
                if (processorCausingMaxDataArrival != candidateProcessor) {
                    earliestStartTimeOnCurrentProcessor = Math.max(earliestStartTimeOnCurrentProcessor, maxDataArrival);
                } else {
                    earliestStartTimeOnCurrentProcessor = Math.max(earliestStartTimeOnCurrentProcessor, secondMaxDataArrival);
                }

                // Exit conditions 2: tighter constraint now that we have selected the processor
                criticalPathConstraint = earliestStartTimeOnCurrentProcessor + maxLengthToExitNode[candidateTask] >= bestFinishTime;
                if (criticalPathConstraint) {
                    continue;
                }

                // Update state (Location 2: Processors)
                int prevFinishTime = state.processorFinishTimes[candidateProcessor];
                state.processorFinishTimes[candidateProcessor] = earliestStartTimeOnCurrentProcessor + taskGraph.getDuration(candidateTask);
                state.scheduledOn[candidateTask] = candidateProcessor;
                state.taskStartTimes[candidateTask] = earliestStartTimeOnCurrentProcessor;

                recursiveSearch(state.getDeepCopy());

                // Backtrack state (Location 2: Processors)
                state.processorFinishTimes[candidateProcessor] = prevFinishTime;
            }

            // Backtrack state (Location 1: Candidate Task)
            for (Integer candidateChild : candidateChildren) {
                // revert changes made to children
                state.inDegrees[candidateChild]++;
                if (state.inDegrees[candidateChild] == 1) {
                    state.candidateTasks.removeLast();
                }
            }
            state.remainingDuration += taskGraph.getDuration(candidateTask);
            state.candidateTasks.add(candidateTask);
            state.taskStartTimes[candidateTask] = -1;
        }
    }

    /**
     * Helper method to create the output Schedule.
     *
     * @return Optimal Schedule.
     */
    private Schedule createOutput() {
        Task[] optimalSchedule = new Task[numTasks];
        for (int i = 0; i < numTasks; i++) {
            Task t = new Task(i, bestStartTime[i],
                    bestStartTime[i] + taskGraph.getDuration(i), bestScheduledOn[i]);
            optimalSchedule[i] = t;
        }

        return new Schedule(optimalSchedule, bestFinishTime);
    }

    /**
     * Find the maximum value integer in the array. Returns Integer.MIN_VALUE if array is empty.
     *
     * @return maximum value.
     */
    private int findMaxInArray(int[] arr) {
        int max = Integer.MIN_VALUE;
        for (int j : arr) {
            max = Math.max(max, j);
        }

        return max;
    }

    /**
     * Helper method to initialize variables used by all threads.
     */
    private void initializeGlobalVars(TaskGraph taskGraph, int numProcessors, int upperBoundTime) {
        this.taskGraph = taskGraph;
        this.numProcessors = numProcessors;
        maxLengthToExitNode = PreProcessor.maxLengthToExitNode(taskGraph);
        nodePriorities = maxLengthToExitNode; //REFACTOR;
        bestFinishTime = upperBoundTime;
        numTasks = taskGraph.getNumberOfTasks();
        equivalentNodesList = PreProcessor.getNodeEquivalence(taskGraph); //REFACTOR
        bestStartTime = new int[numTasks];
        bestScheduledOn = new int[numTasks];
    }

    /**
     * Helper method to create the initial state on which the algorithm runs.
     */
    private State initializeState(TaskGraph taskGraph, int numProcessors) {
        LinkedList<Integer> candidateTasks = new LinkedList<>();
        int[] inDegrees = new int[numTasks];
        int[] taskStartTimes = new int[numTasks];
        Arrays.fill(taskStartTimes, -1);
        int[] scheduledOn = new int[numTasks];
        int[] processorFinishTimes = new int[numProcessors];
        int remainingDuration = 0;
        for (int i = 0; i < numTasks; i++) {
            // calculate remaining duration of tasks to be scheduled
            remainingDuration += taskGraph.getDuration(i);
            inDegrees[i] = taskGraph.getParentsList(i).size();
            if (inDegrees[i] == 0) {
                candidateTasks.add(i);
            }
        }
        return new State(candidateTasks, inDegrees, taskStartTimes,
                scheduledOn, processorFinishTimes, remainingDuration);
    }
}