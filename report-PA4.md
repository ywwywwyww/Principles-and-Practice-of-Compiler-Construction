|  program   | without optimize | dead code elimination | constant propagation + dead code elimination | copy propagation + dead code elimination | copy propagation + constant propagation + dead code elimination |
| :--------: | :--------------: | :-------------------: | :------------------------------------------: | :--------------------------------------: | :----------------------------------------------------------: |
|   basic    |        41        |          41           |                      38                      |                    41                    |                              38                              |
| fibonacci  |       3426       |         3426          |                     3425                     |                   3426                   |                             3425                             |
|    math    |       139        |          139          |                     135                      |                   139                    |                             135                              |
|   queue    |       2536       |         2536          |                     2532                     |                   3536                   |                             2532                             |
|   stack    |       733        |          733          |                     730                      |                   730                    |                             727                              |
| mandelbrot |     3893085      |        3893085        |                   3782375                    |                 3867364                  |                           3756654                            |
|   rbtree   |     2439827      |        2439827        |                   2439816                    |                 2437142                  |                           2437131                            |
|    sort    |      560987      |        560987         |                    559980                    |                  560986                  |                            559979                            |
|  garbage   |     2400009      |        700009         |                    700009                    |                  700008                  |                            700008                            |

