# PA1-A report

## 大致思路

### 抽象类/函数

　　添加了 abstract 关键词。

　　在 ClassDef 类中添加了成员变量 modifiers。

　　在 Modifiers 类中添加了 abstract 选项。

　　把一个类的 modifiers 的 abstract 选项选上就会变成抽象类。

　　把一个函数的 modifiers 的 abstract 选项选上就会变成抽象函数。

### 局部类型推导

　　添加了 var 关键词。

　　把 LocalVarDef 类中的 typeLit 成员变量的类型由 TypeLit 改成了 Optional<TypeLit>。用 null 来表示未确定的类型。

### 函数类型

　　添加了函数类型。

　　新增了一个类 TLambda 表示函数类型。

　　TLambda 类中的 returnType 表示返回值类型，params 表示参数。

### Lambda 表达式

　　新增了关键词 fun 和操作符 '=>'。

　　新增了类 LambdaDef 用来表示一个 Lambda 表达式。

　　LambdaDef 类中的 params 表示参数，type 表示这个 Lambda 表达式是只包含一个表达式还是包含一个块。后面的 expr 和 block 就是表达式和块。

### 函数调用

　　修改了 Call 类以支持函数类型。

　　Call 类只包含两个成员变量，method 表示函数，args 表示参数。

## 遇到的问题

　　经常拼错变量名/漏打符号，要观察很久才能发现。

## 要求回答的问题

### Q1

　　A 表示一个实际的类型（例如二元运算、lambda 表达式），B 表示一个抽象的类型（例如表达式），很多时候我们只需要知道某一个节点的抽象的类型就可以构建 AST，而不用知道这个节点具体是在做什么。