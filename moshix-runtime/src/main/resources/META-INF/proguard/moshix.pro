# Top-level classes: package.Class → package.ClassJsonAdapter
-keepnames @com.squareup.moshi.JsonClass class **.*
-if @com.squareup.moshi.JsonClass class <1>.<2>
-keep class <1>.<2>JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# 1-level nested: package.Outer$Inner → package.Outer_InnerJsonAdapter
-keepnames @com.squareup.moshi.JsonClass class **.*$*
-if @com.squareup.moshi.JsonClass class <1>.<2>$<3>
-keep class <1>.<2>_<3>JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# 2-level nested: package.Outer$Mid$Inner → package.Outer_Mid_InnerJsonAdapter
-keepnames @com.squareup.moshi.JsonClass class **.*$*$*
-if @com.squareup.moshi.JsonClass class <1>.<2>$<3>$<4>
-keep class <1>.<2>_<3>_<4>JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# 3-level nested: package.Outer$L1$L2$Inner → package.Outer_L1_L2_InnerJsonAdapter
-keepnames @com.squareup.moshi.JsonClass class **.*$*$*$*
-if @com.squareup.moshi.JsonClass class <1>.<2>$<3>$<4>$<5>
-keep class <1>.<2>_<3>_<4>_<5>JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# 4-level nested: package.Outer$L1$L2$L3$Inner → package.Outer_L1_L2_L3_InnerJsonAdapter
-keepnames @com.squareup.moshi.JsonClass class **.*$*$*$*$*
-if @com.squareup.moshi.JsonClass class <1>.<2>$<3>$<4>$<5>$<6>
-keep class <1>.<2>_<3>_<4>_<5>_<6>JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# 5-level nested: package.Outer$L1$L2$L3$L4$Inner → package.Outer_L1_L2_L3_L4_InnerJsonAdapter
-keepnames @com.squareup.moshi.JsonClass class **.*$*$*$*$*$*
-if @com.squareup.moshi.JsonClass class <1>.<2>$<3>$<4>$<5>$<6>$<7>
-keep class <1>.<2>_<3>_<4>_<5>_<6>_<7>JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# 6-level nested: package.Outer$L1$L2$L3$L4$L5$Inner → package.Outer_L1_L2_L3_L4_L5_InnerJsonAdapter
-keepnames @com.squareup.moshi.JsonClass class **.*$*$*$*$*$*$*
-if @com.squareup.moshi.JsonClass class <1>.<2>$<3>$<4>$<5>$<6>$<7>$<8>
-keep class <1>.<2>_<3>_<4>_<5>_<6>_<7>_<8>JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# 7-level nested: package.Outer$L1$L2$L3$L4$L5$L6$Inner → package.Outer_L1_L2_L3_L4_L5_L6_InnerJsonAdapter
-keepnames @com.squareup.moshi.JsonClass class **.*$*$*$*$*$*$*$*
-if @com.squareup.moshi.JsonClass class <1>.<2>$<3>$<4>$<5>$<6>$<7>$<8>$<9>
-keep class <1>.<2>_<3>_<4>_<5>_<6>_<7>_<8>_<9>JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# 8-level nested: package.Outer$L1$L2$L3$L4$L5$L6$L7$Inner → package.Outer_L1_L2_L3_L4_L5_L6_L7_InnerJsonAdapter
-keepnames @com.squareup.moshi.JsonClass class **.*$*$*$*$*$*$*$*$*
-if @com.squareup.moshi.JsonClass class <1>.<2>$<3>$<4>$<5>$<6>$<7>$<8>$<9>$<10>
-keep class <1>.<2>_<3>_<4>_<5>_<6>_<7>_<8>_<9>_<10>JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# Any more than this and folks are on their own