|  program   | without optimize | dead code elimination | constant propagation + dead code elimination | copy propagation + dead code elimination | copy propagation + constant propagation + dead code elimination |
| :--------: | :--------------: | :-------------------: | :------------------------------------------: | :--------------------------------------: | :----------------------------------------------------------: |
|   basic    |        41        |                       |                                              |                                          |                              38                              |
| fibonacci  |       3426       |                       |                                              |                                          |                             3425                             |
|    math    |       139        |                       |                                              |                                          |                             135                              |
|   queue    |       2536       |                       |                                              |                                          |                             2492                             |
|   stack    |       733        |                       |                                              |                                          |                             726                              |
| mandelbrot |     3893085      |                       |                                              |                                          |                           3756654                            |
|   rbtree   |     2439827      |                       |                                              |                                          |                           2437131                            |
|    sort    |      560987      |                       |                                              |                                          |                            559979                            |
|  garbage   |     2400009      |                       |                                              |                                          |                            700008                            |

