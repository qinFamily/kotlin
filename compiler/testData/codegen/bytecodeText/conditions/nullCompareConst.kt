// IGNORE_BACKEND: JVM
fun f(): Boolean = "non-primitive" == null
fun g(): Boolean = null == "non-primitive"
fun h(): Boolean = "non-primitive".equals(null)
//fun i(): Boolean = null.equals("non-primitive")
//see nullCompareNonPrimitiveConst

// 0 ACONST_NULL
// 0 INVOKESTATIC
// 0 INVOKEVIRTUAL
// 3 ICONST_0