package org.apache.lucene.util;

import java.util.AbstractList;
import java.util.Comparator;
import java.util.RandomAccess;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of the candidate "TimSorter: 49-entry run stack overflows".
 *
 * <p>TimSorter.ensureInvariants() (TimSorter.java:127-153) is the original Java mergeCollapse
 * without the extra check added to Python/OpenJDK later, and STACKSIZE = 49 (TimSorter.java:45).
 *
 * <ul>
 *   <li>PUBLISHED_RUNS: the run lengths of Auger, Juge, Nicaud and Pivoteau (ESA 2018), taken from
 *       the archived Test.java (web.archive.org/web/20220120125239/http://igm.univ-mlv.fr/~pivoteau/
 *       Timsort/Test.java). n = 1,091,482,190. Its runs go down to 17, below Lucene's minRun(n) = 33.
 *   <li>LEGAL_RUNS: same merge-decision skeleton, run lengths re-optimised by a linear program plus
 *       integer rounding (verify/.../scripts/causal_round.py). Every run except the 1-element final
 *       run is >= 46 >= minRun(n) = 39, n = 1,285,317,058 < Integer.MAX_VALUE, so TimSorter.sort()
 *       detects exactly these runs.
 * </ul>
 *
 * <p>Arrays are 0/1 bit arrays: every run is (len - 1) zeros followed by a one, so each natural run
 * is non-descending and ends at a descent (same encoding as Pivoteau's Test.java and Lucene's own
 * TestTimSorterWorstCase).
 */
public class TestTimSorterStackOverflow extends LuceneTestCase {

  static final int[] PUBLISHED_RUNS = {
    76405736, 74830360, 1181532, 787688, 1575376, 2363064, 3938440, 6301504, 1181532, 393844, 15753760, 1575376,
    787688, 393844, 1969220, 3150752, 1181532, 787688, 5513816, 3938440, 1181532, 787688, 1575376, 18116824,
    1181532, 787688, 1575376, 2363064, 3938440, 787688, 26781392, 1181532, 787688, 1575376, 2363064, 393844,
    4332284, 1181532, 787688, 1575376, 12209164, 1181532, 787688, 1575376, 2363064, 787688, 393844, 4726128,
    1575376, 787688, 1969220, 76405758, 53168940, 1181532, 787688, 1575376, 2363064, 3938440, 1575376, 787688,
    393844, 10633788, 1181532, 787688, 1575376, 2363064, 4332284, 1181532, 787688, 1575376, 12996852, 1181532,
    787688, 1575376, 2363064, 393844, 17329136, 1575376, 787688, 393844, 1969220, 3150752, 1181532, 393844,
    7483036, 1575376, 787688, 1969220, 2756908, 1181532, 787600, 76405780, 38202802, 114608494, 66, 44,
    88, 176, 352, 704, 1408, 2816, 5632, 11264, 22528, 45056, 90112, 180224,
    360448, 720896, 1441792, 2883584, 5767168, 11387222, 22495132, 319836, 213224, 426448, 639672, 1066120,
    1705792, 426448, 213224, 106612, 4584316, 426448, 213224, 106612, 533060, 106612, 852896, 426448,
    213224, 1599180, 1172732, 319836, 213224, 426448, 5223988, 319836, 213224, 426448, 639672, 1066120,
    319836, 213224, 7782676, 426448, 213224, 533060, 746284, 213224, 1705792, 319836, 213224, 426448,
    639672, 2238852, 426448, 213224, 106612, 2345464, 426448, 213224, 106612, 533060, 106612, 852896,
    426448, 213224, 106612, 22921602, 15245516, 319836, 213224, 426448, 639672, 1172732, 319836, 213224,
    426448, 3304972, 319836, 213224, 426448, 639672, 213224, 1279344, 426448, 213224, 533060, 3838032,
    319836, 213224, 426448, 639672, 213224, 106612, 5330600, 319836, 213224, 426448, 639672, 1066120,
    213224, 2345464, 426448, 213224, 106612, 533060, 106612, 852896, 426448, 213224, 106524, 22921624,
    11460724, 34382260, 66, 44, 88, 176, 352, 704, 1408, 2816, 5632, 11264,
    22528, 45056, 90112, 180224, 360448, 720896, 1001792, 1783584, 2649020, 6739370, 102630, 68420,
    136840, 205260, 342100, 547360, 102630, 68420, 1436820, 102630, 68420, 136840, 205260, 342100,
    547360, 102630, 68420, 136840, 205260, 68420, 34210, 1607870, 102630, 68420, 136840, 205260,
    342100, 68420, 34210, 2428910, 102630, 68420, 136840, 205260, 34210, 410520, 102630, 68420,
    136840, 1094720, 102630, 68420, 136840, 205260, 68420, 34210, 444730, 136840, 68420, 34210,
    171050, 34210, 6876232, 4618350, 102630, 68420, 136840, 205260, 34210, 342100, 136840, 68420,
    34210, 992090, 102630, 68420, 136840, 205260, 68420, 342100, 205260, 102630, 68420, 1163140,
    102630, 68420, 136840, 205260, 68420, 1607870, 102630, 68420, 136840, 205260, 342100, 34210,
    684200, 136840, 68420, 171050, 239470, 102630, 68332, 6876254, 3438028, 10314194, 66, 44,
    88, 176, 352, 704, 1408, 2816, 5632, 11264, 22528, 45056, 90112, 180224,
    360448, 500896, 840554, 2018720, 32736, 21824, 43648, 65472, 21824, 10912, 141856, 43648,
    21824, 10912, 54560, 10912, 425568, 43648, 21824, 54560, 76384, 21824, 10912, 185504,
    32736, 21824, 43648, 65472, 21824, 10912, 491040, 32736, 21824, 43648, 65472, 109120,
    10912, 731104, 32736, 21824, 43648, 65472, 120032, 32736, 21824, 43648, 327360, 32736,
    21824, 43648, 65472, 21824, 130944, 43648, 21824, 54560, 2062390, 1396736, 32736, 21824,
    43648, 65472, 109120, 43648, 21824, 10912, 294624, 32736, 21824, 43648, 65472, 120032,
    32736, 21824, 43648, 360096, 32736, 21824, 43648, 65472, 10912, 480128, 32736, 21824,
    43648, 65472, 109120, 196416, 65472, 32736, 10912, 76384, 21824, 10824, 2062412, 1031118,
    3093442, 66, 44, 88, 176, 352, 704, 1408, 2816, 5632, 11264, 22528,
    45056, 90112, 180224, 258170, 605616, 9768, 6512, 13024, 19536, 6512, 3256, 42328,
    13024, 6512, 3256, 16280, 3256, 126984, 13024, 6512, 16280, 22792, 6512, 3256,
    55352, 9768, 6512, 13024, 19536, 6512, 3256, 146520, 9768, 6512, 13024, 19536,
    32560, 3256, 218152, 9768, 6512, 13024, 19536, 35816, 9768, 6512, 13024, 100936,
    9768, 6512, 13024, 19536, 6512, 3256, 39072, 13024, 6512, 16280, 618662, 416768,
    9768, 6512, 13024, 19536, 32560, 13024, 6512, 3256, 87912, 9768, 6512, 13024,
    19536, 35816, 9768, 6512, 13024, 107448, 9768, 6512, 13024, 19536, 3256, 143264,
    13024, 6512, 3256, 16280, 26048, 9768, 3256, 61864, 13024, 6512, 16280, 22792,
    9768, 3168, 618684, 309254, 927850, 66, 44, 88, 176, 352, 704, 1408,
    2816, 5632, 11264, 22440, 23056, 45056, 72314, 181632, 2838, 1892, 3784, 5676,
    9460, 15136, 2838, 946, 37840, 3784, 1892, 946, 4730, 7568, 2838, 1892,
    13244, 9460, 2838, 1892, 3784, 43516, 2838, 1892, 3784, 5676, 9460, 1892,
    65274, 2838, 1892, 3784, 5676, 946, 10406, 2838, 1892, 3784, 30272, 2838,
    1892, 3784, 5676, 1892, 946, 12298, 3784, 1892, 946, 4730, 185438, 127710,
    2838, 1892, 3784, 5676, 9460, 3784, 1892, 946, 26488, 2838, 1892, 3784,
    5676, 946, 10406, 2838, 1892, 3784, 31218, 2838, 1892, 3784, 5676, 946,
    42570, 2838, 1892, 3784, 5676, 9460, 17974, 3784, 1892, 4730, 6622, 2838,
    1804, 185460, 92642, 278014, 66, 44, 88, 176, 352, 704, 1408, 2816,
    5632, 9064, 11528, 23606, 54340, 858, 572, 1144, 1716, 2860, 4576, 858,
    286, 11440, 1144, 572, 286, 1430, 2288, 858, 572, 4004, 2860, 858,
    572, 1144, 13156, 858, 572, 1144, 1716, 2860, 572, 19448, 858, 572,
    1144, 1716, 286, 3146, 858, 572, 1144, 8866, 858, 572, 1144, 1716,
    572, 286, 3432, 1144, 572, 1430, 55506, 38610, 858, 572, 1144, 1716,
    2860, 1144, 572, 286, 7722, 858, 572, 1144, 1716, 3146, 858, 572,
    1144, 9438, 858, 572, 1144, 1716, 286, 12584, 1144, 572, 286, 1430,
    2288, 858, 286, 5434, 1144, 572, 1430, 2002, 858, 484, 55528, 27676,
    83116, 66, 44, 88, 176, 352, 704, 1408, 1716, 3872, 8118, 16192,
    264, 176, 352, 528, 176, 88, 1144, 352, 176, 88, 440, 88,
    3432, 352, 176, 440, 616, 176, 88, 1408, 264, 176, 352, 528,
    176, 88, 3960, 264, 176, 352, 528, 880, 88, 5808, 264, 176,
    352, 528, 968, 264, 176, 352, 2640, 264, 176, 352, 528, 176,
    1056, 352, 176, 440, 16566, 11264, 264, 176, 352, 528, 880, 352,
    176, 2376, 264, 176, 352, 528, 968, 264, 176, 352, 2816, 264,
    176, 352, 528, 88, 3872, 264, 176, 352, 528, 880, 1584, 528,
    264, 88, 616, 176, 16588, 8206, 24706, 66, 44, 88, 176, 352,
    704, 1408, 2090, 4708, 66, 44, 88, 132, 220, 352, 66, 44,
    88, 990, 66, 44, 88, 132, 220, 418, 88, 44, 110, 154,
    66, 44, 1122, 66, 44, 88, 132, 220, 88, 44, 22, 1716,
    88, 44, 110, 154, 44, 352, 66, 44, 88, 132, 44, 22,
    462, 110, 44, 22, 528, 88, 44, 22, 110, 22, 176, 88,
    44, 22, 4950, 3256, 66, 44, 88, 132, 22, 242, 66, 44,
    88, 704, 66, 44, 88, 132, 44, 22, 264, 88, 44, 110,
    814, 88, 44, 110, 154, 22, 1144, 66, 44, 88, 132, 220,
    44, 22, 506, 88, 44, 22, 110, 22, 176, 88, 44, 22,
    4972, 2398, 7282, 66, 44, 88, 176, 242, 418, 660, 1496, 66,
    44, 88, 132, 242, 66, 44, 88, 682, 66, 44, 88, 132,
    44, 22, 264, 88, 44, 110, 1716, 858, 88, 44, 110, 154,
    44, 22, 352, 66, 44, 88, 132, 44, 22, 1738, 198, 1760,
    175, 156, 18, 17, 19, 36, 65, 21, 20, 22, 18, 452,
    114, 95, 18, 17, 21, 36, 18, 17, 115, 76, 144, 44,
    38, 61, 20, 19, 21, 17,
  };

  static final int[] LEGAL_RUNS = {
    84316538, 83807004, 509530, 509526, 1019051, 2038102, 4076205, 5434940, 2717470, 2717466, 17796503, 1225783,
    612892, 612888, 2451558, 3268745, 1634372, 1634368, 6355893, 5084709, 1271179, 1271175, 2542351, 19022294,
    2294247, 46, 2294289, 2294297, 6882828, 46, 31996657, 791384, 791380, 1582761, 1582769, 1582753,
    5064833, 1266210, 1266206, 2532413, 15535943, 924763, 924759, 1849517, 2466023, 1233011, 1233007, 6904859,
    1726217, 1726213, 3452426, 84316542, 64343202, 3495108, 46, 3495150, 6990300, 6990308, 100, 50,
    46, 20841115, 129989, 129985, 129993, 389962, 389970, 194958, 46, 194962, 20971112, 238310,
    238306, 238314, 714879, 46, 20971116, 1429846, 1429796, 46, 2859685, 2859710, 2859660, 46,
    9532322, 1906464, 1906460, 1906468, 3812927, 1906460, 46, 84316546, 49590404, 133906893, 52, 48,
    97, 193, 387, 774, 1548, 3096, 6191, 12383, 24765, 49531, 99062, 198123,
    396247, 792493, 1584986, 3169972, 6339944, 12679889, 25210756, 149022, 149018, 298035, 596070, 1192141,
    1430571, 953711, 476856, 476852, 5353529, 368742, 184371, 184367, 737429, 46, 983300, 491650,
    491646, 1911974, 1529574, 382396, 382392, 764783, 5722279, 223962, 223958, 447916, 895831, 1194442,
    597221, 597217, 9585645, 317176, 317172, 634344, 634352, 634336, 2255442, 281933, 281929, 563858,
    1127717, 2537383, 1242023, 50, 46, 3268752, 510745, 510695, 46, 510749, 510733, 1225780,
    817184, 408592, 408588, 25359784, 24917475, 442306, 442302, 442310, 1326915, 2653780, 50, 46,
    54, 4776984, 530776, 530772, 530780, 1592277, 46, 1592331, 796119, 46, 796161, 5307767,
    729812, 729808, 1459615, 1459640, 1459590, 46, 5307771, 58, 46, 62, 162, 170,
    154, 550, 100, 50, 46, 104, 46, 200, 100, 50, 46, 25359789,
    16233891, 41593626, 50, 46, 92, 184, 368, 736, 1472, 2944, 5888, 11776,
    23552, 47104, 94208, 188416, 376832, 753664, 1507328, 1507336, 3441920, 7917456, 46457, 46453,
    92906, 185811, 371622, 495496, 247748, 247744, 1678130, 56104, 56100, 112200, 224400, 448801,
    780520, 117081, 117077, 234154, 312205, 156102, 156098, 1734242, 199263, 46, 199305, 199317,
    398617, 199309, 199305, 3053804, 75593, 75589, 151179, 302312, 46, 483771, 120945, 120941,
    241881, 1481543, 90711, 90707, 181413, 241884, 120942, 120938, 634944, 211647, 105823, 105819,
    211651, 211635, 7963920, 7861428, 102489, 102485, 204969, 204977, 204961, 819777, 100, 50,
    46, 1405624, 234226, 46, 234268, 468490, 46, 468544, 468490, 50, 46, 1639859,
    273275, 46, 273317, 273325, 46, 2459814, 50, 46, 54, 100, 154, 46,
    258, 50, 46, 58, 100, 50, 46, 7963924, 4868987, 12832858, 50, 46,
    92, 184, 368, 736, 1472, 2944, 5888, 11776, 23552, 47104, 94208, 188416,
    376832, 753664, 969439, 2455537, 21230, 21226, 42452, 56603, 28301, 28297, 148580, 49525,
    24763, 24759, 49529, 49513, 515243, 29532, 29528, 59056, 78742, 39371, 39367, 239643,
    35949, 35945, 71890, 95854, 47927, 47923, 544783, 26399, 26395, 52789, 105578, 211110,
    46, 943646, 23449, 23445, 46890, 93781, 150048, 37514, 37510, 75020, 455985, 31669,
    31665, 63330, 126615, 46, 202655, 50666, 50662, 101324, 2476775, 1955949, 38805, 38801,
    38809, 116411, 116419, 116365, 50, 46, 419131, 46570, 46566, 46574, 93140, 186276,
    46571, 46567, 93134, 465709, 155202, 46, 155206, 155252, 93104, 465713, 23293, 23289,
    46577, 46585, 93163, 232803, 100, 50, 46, 104, 50, 46, 2476778, 1462894,
    3939619, 50, 46, 93, 186, 371, 742, 1485, 2969, 5939, 11878, 23755,
    47511, 95022, 190043, 380086, 753653, 6519, 6515, 13031, 17375, 8687, 8683, 45606,
    15201, 15151, 46, 15205, 15189, 158138, 9067, 9063, 18126, 24168, 12084, 12080,
    73548, 11035, 11031, 22062, 29416, 14708, 14704, 167213, 12959, 12955, 12963, 25919,
    38882, 25911, 289630, 7168, 7164, 14327, 28655, 45846, 11464, 11460, 22919, 140624,
    8375, 8371, 16741, 22321, 11161, 11157, 62495, 15626, 15622, 31244, 760179, 741501,
    18676, 46, 18718, 37435, 49898, 24972, 24922, 46, 172413, 2296, 46, 2300,
    4637, 7418, 1856, 1852, 3705, 174716, 5405, 46, 5447, 10849, 46, 174720,
    21786, 50, 46, 21790, 21886, 21782, 46, 87331, 50, 46, 92, 100,
    50, 46, 760184, 449464, 1209594, 50, 46, 92, 184, 368, 736, 1472,
    2895, 5839, 5847, 11686, 29212, 58424, 116847, 232278, 1416, 1412, 2825, 5650,
    11299, 15065, 7533, 7529, 49324, 3401, 1700, 1696, 6794, 9059, 4529, 4525,
    17614, 14087, 3524, 3520, 7039, 52733, 6311, 46, 6353, 6361, 12714, 6353,
    88668, 2197, 2193, 4387, 4395, 4379, 14035, 3511, 3507, 7014, 43047, 2566,
    2562, 5125, 6833, 3416, 3412, 19128, 4784, 2392, 2388, 9560, 233703, 178973,
    4640, 4636, 4644, 9281, 23098, 100, 50, 46, 42913, 3579, 3575, 7150,
    14253, 46, 14307, 50, 46, 54, 46500, 3624, 3620, 3628, 10823, 46,
    64222, 4017, 4013, 4021, 8033, 12054, 32082, 50, 46, 58, 100, 50,
    46, 233707, 137406, 371059, 50, 46, 92, 184, 368, 736, 1472, 2944,
    5888, 11776, 12298, 35850, 71262, 437, 433, 867, 1734, 3468, 4623, 2312,
    2308, 15132, 1046, 523, 519, 2084, 2778, 1389, 1385, 5403, 4318, 1081,
    1077, 2155, 16186, 975, 971, 979, 2921, 5796, 46, 27194, 677, 673,
    1346, 2645, 46, 4305, 1078, 1074, 2148, 13198, 790, 786, 1571, 2095,
    1048, 1044, 5861, 1467, 1463, 2927, 71708, 71023, 681, 677, 1353, 2668,
    2714, 2660, 50, 46, 9858, 987, 983, 1967, 1975, 3942, 1944, 46,
    1948, 10853, 1602, 1598, 3195, 3203, 3187, 10857, 906, 453, 449, 1805,
    1813, 906, 902, 3618, 722, 718, 730, 1444, 722, 718, 71712, 42130,
    113787, 50, 46, 92, 184, 368, 736, 1472, 2944, 5888, 10201, 21784,
    193, 189, 379, 505, 253, 249, 1324, 440, 390, 46, 444, 428,
    4570, 266, 262, 524, 699, 349, 345, 2121, 321, 317, 634, 845,
    423, 419, 4844, 424, 46, 466, 931, 939, 923, 8358, 212, 208,
    416, 832, 1329, 334, 330, 660, 4033, 284, 280, 559, 1073, 46,
    1788, 449, 445, 890, 21985, 16237, 511, 46, 515, 1033, 1075, 1025,
    46, 3791, 456, 46, 460, 958, 1866, 50, 46, 54, 4255, 339,
    335, 343, 966, 46, 5692, 586, 46, 628, 636, 1892, 1900, 877,
    50, 46, 923, 46, 21989, 12941, 34871, 55, 51, 103, 205, 411,
    822, 1644, 3288, 6525, 50, 46, 86, 178, 355, 363, 120, 116,
    124, 1383, 50, 46, 92, 184, 368, 638, 99, 95, 189, 252,
    126, 122, 1441, 107, 46, 111, 260, 420, 100, 50, 46, 2496,
    80, 76, 153, 161, 145, 529, 82, 78, 156, 208, 104, 100,
    619, 290, 50, 46, 864, 137, 69, 65, 221, 46, 322, 212,
    106, 102, 6583, 5230, 230, 46, 272, 280, 46, 551, 50, 46,
    54, 1264, 50, 46, 92, 123, 61, 57, 192, 63, 59, 67,
    1579, 50, 46, 54, 100, 46, 1769, 102, 98, 106, 267, 309,
    259, 46, 577, 100, 50, 46, 104, 65, 208, 154, 50, 46,
    6587, 4284, 10817, 50, 46, 92, 184, 368, 433, 1169, 2277, 61,
    57, 113, 226, 360, 92, 88, 176, 1100, 70, 66, 131, 175,
    87, 83, 484, 123, 119, 238, 2346, 1548, 128, 124, 248, 445,
    50, 46, 503, 115, 46, 157, 182, 132, 46, 2350, 950, 2810,
    486, 432, 50, 46, 54, 100, 178, 58, 54, 62, 46, 1240,
    312, 258, 50, 46, 58, 100, 50, 46, 316, 87, 320, 79,
    75, 100, 50, 46, 1,
  };

  static long sum(int[] runs) {
    long s = 0;
    for (int r : runs) s += r;
    return s;
  }

  /** compare() == 0, so TimSorter.merge() returns at its first check: only stack bookkeeping runs. */
  static final class StackReplay extends TimSorter {
    StackReplay() {
      super(0);
    }

    @Override
    protected int compare(int i, int j) {
      return 0;
    }

    @Override
    protected void swap(int i, int j) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void copy(int src, int dest) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void save(int i, int len) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void restore(int i, int j) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected int compareSaved(int i, int j) {
      throw new UnsupportedOperationException();
    }
  }

  /** Feeds run lengths through the loop body of TimSorter.sort(): ensureInvariants(); pushRunLen(). */
  static int replay(int[] runs, int[] pushed) {
    return replay(new StackReplay(), runs, pushed);
  }

  static int replay(StackReplay s, int[] runs, int[] pushed) {
    s.reset(0, (int) sum(runs));
    int max = 0;
    for (int r : runs) {
      s.ensureInvariants();
      s.pushRunLen(r);
      pushed[0]++;
      max = Math.max(max, s.stackSize);
    }
    return max;
  }

  /** 0/1 array: a one at the last slot of every run; the last run is left as is if lastRunHasOne. */
  static FixedBitSet encode(int[] runs, boolean lastRunHasOne) {
    long n = sum(runs);
    assertTrue(n <= Integer.MAX_VALUE);
    FixedBitSet bits = new FixedBitSet((int) n);
    long end = -1;
    for (int i = 0; i < runs.length; i++) {
      end += runs[i];
      if (i < runs.length - 1 || lastRunHasOne) bits.set((int) end);
    }
    return bits;
  }

  /** A real TimSorter over a bit array; maxTempSlots = n / 64 as in ArrayUtil/CollectionUtil. */
  static final class BitTimSorter extends TimSorter {
    final FixedBitSet a;
    final FixedBitSet tmp;
    int maxStackSeen;

    BitTimSorter(FixedBitSet a, int maxTempSlots) {
      super(maxTempSlots);
      this.a = a;
      this.tmp = new FixedBitSet(Math.max(1, maxTempSlots));
    }

    private static void put(FixedBitSet b, int i, boolean v) {
      if (v) b.set(i);
      else b.clear(i);
    }

    @Override
    protected int compare(int i, int j) {
      maxStackSeen = Math.max(maxStackSeen, stackSize); // observation only
      return Boolean.compare(a.get(i), a.get(j));
    }

    @Override
    protected void swap(int i, int j) {
      boolean x = a.get(i);
      put(a, i, a.get(j));
      put(a, j, x);
    }

    @Override
    protected void copy(int src, int dest) {
      put(a, dest, a.get(src));
    }

    @Override
    protected void save(int i, int len) {
      for (int k = 0; k < len; k++) put(tmp, k, a.get(i + k));
    }

    @Override
    protected void restore(int i, int j) {
      put(a, j, tmp.get(i));
    }

    @Override
    protected int compareSaved(int i, int j) {
      return Boolean.compare(tmp.get(i), a.get(j));
    }
  }

  /** A RandomAccess List<Boolean> view over a bit array, for the public CollectionUtil.timSort. */
  static final class BitList extends AbstractList<Boolean> implements RandomAccess {
    final FixedBitSet bits;
    final int size;

    BitList(FixedBitSet bits, int size) {
      this.bits = bits;
      this.size = size;
    }

    @Override
    public Boolean get(int i) {
      return bits.get(i);
    }

    @Override
    public Boolean set(int i, Boolean v) {
      boolean old = bits.get(i);
      if (v) bits.set(i);
      else bits.clear(i);
      return old;
    }

    @Override
    public int size() {
      return size;
    }
  }

  static void assertPushRunLenOverflow(ArrayIndexOutOfBoundsException e) {
    StackTraceElement top = e.getStackTrace()[0];
    assertEquals(TimSorter.class.getName(), top.getClassName());
    assertEquals("pushRunLen", top.getMethodName());
    assertEquals("Index 50 out of bounds for length 50", e.getMessage());
  }

  // 1. The candidate's literal observation (internal replay of the published sequence).
  public void testPublishedRunsOverflowWhenReplayedThroughInternals() {
    assertEquals(1062, PUBLISHED_RUNS.length);
    assertEquals(1_091_482_190L, sum(PUBLISHED_RUNS));
    int[] pushed = new int[1];
    ArrayIndexOutOfBoundsException e =
        expectThrows(ArrayIndexOutOfBoundsException.class, () -> replay(PUBLISHED_RUNS, pushed));
    assertPushRunLenOverflow(e);
    assertEquals(1060, pushed[0]); // the 1061st push fails, i.e. 49 runs were on the stack
    System.out.println("published runs: replay overflowed at push #" + (pushed[0] + 1));
  }

  // 2. The published sequence is not something sort() can produce: runs < minRun(n).
  public void testPublishedRunsAreNotALegalSortInput() {
    int n = (int) sum(PUBLISHED_RUNS);
    int min = Integer.MAX_VALUE;
    for (int i = 0; i < PUBLISHED_RUNS.length - 1; i++) min = Math.min(min, PUBLISHED_RUNS[i]);
    assertEquals(17, min);
    assertEquals(33, TimSorter.minRun(n));
  }

  // 3. Real sort() on the published 0/1 array (Pivoteau's main()): no overflow, correct result.
  public void testRealSortOfPublishedArraySucceeds() {
    int n = (int) sum(PUBLISHED_RUNS);
    FixedBitSet bits = encode(PUBLISHED_RUNS, true);
    BitTimSorter sorter = new BitTimSorter(bits, n / 64);
    long t0 = System.nanoTime();
    sorter.sort(0, n);
    long ms = (System.nanoTime() - t0) / 1_000_000;
    assertEquals(PUBLISHED_RUNS.length, bits.cardinality());
    assertEquals(n - PUBLISHED_RUNS.length, bits.nextSetBit(0)); // all zeros then all ones
    assertTrue(sorter.maxStackSeen <= TimSorter.STACKSIZE);
    System.out.println(
        "published array: real sort OK in " + ms + " ms, max stackSize seen " + sorter.maxStackSeen);
  }

  // 4. The re-optimised sequence is a legal sort() input.
  public void testLegalRunsAreLegal() {
    long n = sum(LEGAL_RUNS);
    assertEquals(1_285_317_058L, n);
    assertTrue(n < Integer.MAX_VALUE);
    int minRun = TimSorter.minRun((int) n);
    assertEquals(39, minRun);
    for (int i = 0; i < LEGAL_RUNS.length - 1; i++) {
      assertTrue("run " + i + " = " + LEGAL_RUNS[i], LEGAL_RUNS[i] >= minRun);
    }
    assertEquals(1, LEGAL_RUNS[LEGAL_RUNS.length - 1]); // final run: the last element only
  }

  // 5. Internal replay of the legal sequence overflows at the same push.
  public void testLegalRunsOverflowWhenReplayedThroughInternals() {
    int[] pushed = new int[1];
    ArrayIndexOutOfBoundsException e =
        expectThrows(ArrayIndexOutOfBoundsException.class, () -> replay(LEGAL_RUNS, pushed));
    assertPushRunLenOverflow(e);
    assertEquals(LEGAL_RUNS.length - 1, pushed[0]);
  }

  // 6. Real TimSorter.sort() (unmodified algorithm, real merges) on the legal 0/1 array overflows,
  //    and its run stack at that moment is identical to the internal replay's.
  public void testRealTimSorterSortOverflowsOnLegalInput() {
    int n = (int) sum(LEGAL_RUNS);
    FixedBitSet bits = encode(LEGAL_RUNS, false);
    BitTimSorter sorter = new BitTimSorter(bits, n / 64);
    long t0 = System.nanoTime();
    ArrayIndexOutOfBoundsException e =
        expectThrows(ArrayIndexOutOfBoundsException.class, () -> sorter.sort(0, n));
    long ms = (System.nanoTime() - t0) / 1_000_000;
    assertPushRunLenOverflow(e);
    assertEquals(TimSorter.STACKSIZE, sorter.stackSize);
    assertEquals(LEGAL_RUNS.length - 1, bits.cardinality()); // array is still a permutation
    StackReplay replay = new StackReplay();
    expectThrows(
        ArrayIndexOutOfBoundsException.class, () -> replay(replay, LEGAL_RUNS, new int[1]));
    assertArrayEquals(replay.runEnds, sorter.runEnds);
    System.out.println("legal array: TimSorter.sort threw " + e + " after " + ms + " ms");
    StringBuilder sb = new StringBuilder("legal array: run stack (bottom->top) at the failing push:");
    for (int i = 1; i <= sorter.stackSize; i++) sb.append(' ').append(sorter.runEnds[i] - sorter.runEnds[i - 1]);
    System.out.println(sb);
  }

  // 7. Same through the public API CollectionUtil.timSort (ListTimSorter).
  public void testCollectionUtilTimSortOverflowsOnLegalInput() {
    int n = (int) sum(LEGAL_RUNS);
    BitList list = new BitList(encode(LEGAL_RUNS, false), n);
    long t0 = System.nanoTime();
    ArrayIndexOutOfBoundsException e =
        expectThrows(
            ArrayIndexOutOfBoundsException.class,
            () -> CollectionUtil.timSort(list, Comparator.<Boolean>naturalOrder()));
    long ms = (System.nanoTime() - t0) / 1_000_000;
    assertPushRunLenOverflow(e);
    assertEquals(LEGAL_RUNS.length - 1, list.bits.cardinality()); // still a permutation, unsorted
    assertTrue(list.bits.nextSetBit(0) < n - (LEGAL_RUNS.length - 1));
    System.out.println("legal list: CollectionUtil.timSort threw " + e + " after " + ms + " ms");
  }

  // 8. Public API on a plain Java array (like Pivoteau's Test.java for the JDK). Needs ~5.2 GB heap;
  //    run with -Dtests.heavy=true -Xmx8g.
  public void testArrayUtilTimSortOverflowsOnLegalBooleanArray() {
    assumeTrue("needs -Dtests.heavy=true", Boolean.getBoolean("tests.heavy"));
    int n = (int) sum(LEGAL_RUNS);
    Boolean[] a = new Boolean[n];
    java.util.Arrays.fill(a, Boolean.FALSE);
    long end = -1;
    for (int i = 0; i < LEGAL_RUNS.length - 1; i++) {
      end += LEGAL_RUNS[i];
      a[(int) end] = Boolean.TRUE;
    }
    long t0 = System.nanoTime();
    ArrayIndexOutOfBoundsException e =
        expectThrows(ArrayIndexOutOfBoundsException.class, () -> ArrayUtil.timSort(a));
    long ms = (System.nanoTime() - t0) / 1_000_000;
    assertPushRunLenOverflow(e);
    System.out.println("legal Boolean[]: ArrayUtil.timSort threw " + e + " after " + ms + " ms");
  }
}
