package com.zuno.bir.enums;

public enum ItemTag {
    NONE,           // 无标签
    MGS,            // Mandatory (SC/PWD/NAAC/MOV) - 免税+折扣
    VBN,            // Basic Necessities (SC/PWD) - 含税+折扣
    SP,             // Solo Parent - 免税+折扣
    NAAC,           // 某些特定场景可能单独标记
    MOV,
    DIPLOMATIC      // Diplomatic - 免税+0折扣
}
