# Testing
Task scheduling is a complex problem, and it is difficult to conclude that our solution 
is 100% correct, since we can't feasibly test for every single scenario. We however, do 
many tests to ensure that our solution should be correct.

## Solution Validator
We have created a `SolutionValidator` that will test whether the generated solution is 
a valid one. A valid solution has the same tasks, durations, and weights of the input,
and the tasks are arranged on the processors such that dependencies are respected.

## Random Graph Generator
We have created a random graph generator that takes in a set of parameters and can be 
used to test the solution. `RandomDotGraphGenerator` takes several command line 
customisations: `N [-minSize m] [-maxSize x] [-maxTaskWeight t] [-maxEdgeWeight w]`
* `N`: Compulsory. This is the number of graphs to generate.
* `minSize`: This is the minimum number of tasks in our graphs to generate. Default is 0.
* `maxSize`: This is the maximum number of tasks in our graphs to generate. Default is 20.
* `maxTaskWeight`: This is the maximum weight of the tasks in our graphs. Default is 100.
* `maxEdgeWeight`: This is the maximum weight of the edges in our graphs. Default is 100.

## Random Graph Tester
`RandomGraphTester` uses the random graphs generated from `RandomDotGraphGenerator`, 
generates output dot graphs using `scheduler.jar`, and compares the input and output 
using `SolutionValidator`. Both input and output random graph files are deleted after 
execution of `RandomGraphTester`.

Note that although we can test for validity using our `RandomGraphTester`, it is very 
difficult to check for optimality, since to run code for it we also need to guarantee 
that that code gives an optimal solution, giving us a cyclic problem.

## Test Cases
Therefore, we turn to human test cases. We have created JUnit test cases in `Tester`
that cover different aspects of the code. Output from these test cases is run through 
the `SolutionValidator`, and the output finish time is compared with a 
human-calculated best output. 

