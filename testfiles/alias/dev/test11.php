<?php

// may-aliases between formals

a();

function a() {
    if ($u) {
        $a1 =& $a2;
    }
    b(&$a1, &$a2);
}

function b(&$bp1, &$bp2) {
    ~_hotspot0;     // bp1 and bp2 are may-aliases here:
                    // u{(b.bp1, b.bp1_fs) (b.bp2, b.bp2_fs)}
                    // a{(b.bp1, b.bp2) (b.bp1, b.bp2_fs) (b.bp2, b.bp1_fs) (b.bp1_fs, b.bp2_fs)}

}
?>