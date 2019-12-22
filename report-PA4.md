|  program   | without optimize | dead code elimination | constant propagation + dead code elimination | copy propagation + constant propagation + dead code elimination | copy propagator + dead code elimination |
| :--------: | :--------------: | :-------------------: | :------------------------------------------: | :----------------------------------------------------------: | :-------------------------------------: |
|   basic    |        41        |          41           |                      38                      |                              38                              |                   41                    |
| fibonacci  |       3426       |         3426          |                     3425                     |                             3425                             |                  3426                   |
|    math    |       139        |          139          |                     135                      |                             135                              |                   139                   |
|   queue    |       2536       |         2536          |                     2532                     |                             2532                             |                  3526                   |
|   stack    |       733        |          733          |                     730                      |                             730                              |                   733                   |
| mandelbrot |     3893085      |        3893085        |                   3782375                    |                           3782375                            |                 3893085                 |
|   rbtree   |     2439827      |        2439827        |                   2439816                    |                           2437316                            |                 2437327                 |
|    sort    |      560987      |        560987         |                    559980                    |                            559980                            |                 560987                  |

