package buddybin;

import java.io.File;
import java.util.Scanner;


public class Main {

    static final int KB = 1024;
    static final int TOTAL_BYTES = 4 * 1024 * 1024;
    static final int UNIT_BYTES = KB;
    static final int UNITS = TOTAL_BYTES / UNIT_BYTES;

    static final int MAX_LEVEL = 12;
    static final int LEVELS = MAX_LEVEL + 1;

    static int[] freeHead = new int[LEVELS];
    static int[] next = new int[UNITS];
    static int[] freeBlockLevel = new int[UNITS];
    static int[] allocBlockLevel = new int[UNITS];

    static final int MAX_PROGS = 26;
    static char[] progLabel = new char[MAX_PROGS];
    static int[] progRequestedBytes = new int[MAX_PROGS];
    static int progCount = 0;

    static final int MAX_BLOCKS_PER_PROG = 128;
    static int[] progBlockStart = new int[MAX_PROGS * MAX_BLOCKS_PER_PROG];
    static int[] progBlockLevel = new int[MAX_PROGS * MAX_BLOCKS_PER_PROG];
    static int[] progBlockCount = new int[MAX_PROGS];

    static void init() {
        int i = 0;
        while (i < UNITS) {
            next[i] = -1;
            freeBlockLevel[i] = -1;
            allocBlockLevel[i] = -1;
            i = i + 1;
        }
        int l = 0;
        while (l < LEVELS) {
            freeHead[l] = -1;
            l = l + 1;
        }
        freeHead[MAX_LEVEL] = 0;
        freeBlockLevel[0] = MAX_LEVEL;
        next[0] = -1;

        int p = 0;
        while (p < MAX_PROGS) {
            progLabel[p] = 0;
            progRequestedBytes[p] = 0;
            progBlockCount[p] = 0;
            p = p + 1;
        }
        int slots = MAX_PROGS * MAX_BLOCKS_PER_PROG;
        int b = 0;
        while (b < slots) {
            progBlockStart[b] = -1;
            progBlockLevel[b] = -1;
            b = b + 1;
        }
        progCount = 0;
    }

    static void registerProgram(char label, int requestedBytes) {
        if (progCount >= MAX_PROGS) return;
        if (requestedBytes < UNIT_BYTES) requestedBytes = UNIT_BYTES;
        if (requestedBytes > 2 * 1024 * 1024) requestedBytes = 2 * 1024 * 1024;
        progLabel[progCount] = label;
        progRequestedBytes[progCount] = requestedBytes;
        progBlockCount[progCount] = 0;
        progCount = progCount + 1;
    }

    static void loadFile(String filename) {
        try {
            Scanner sc = new Scanner(new File(filename));
            while (sc.hasNext()) {
                String tok = sc.next();
                if (tok == null) break;
                char label = tok.charAt(0);
                if (!sc.hasNextInt()) break;
                int size = sc.nextInt();
                registerProgram(label, size);
            }
            sc.close();
            System.out.println("Arquivo carregado: " + filename);
        } catch (Exception e) {
            System.out.println("Não foi possível ler " + filename + " (ignorando).");
        }
    }

    static int levelOfUnits(int unitsCount) {
        int level = 0;
        int v = 1;
        while (v < unitsCount && level < MAX_LEVEL) {
            v = v * 2;
            level = level + 1;
        }
        return level;
    }

    static int popFreeHead(int level) {
        int head = freeHead[level];
        if (head == -1) return -1;
        int nx = next[head];
        freeHead[level] = nx;
        next[head] = -1;
        freeBlockLevel[head] = -1;
        return head;
    }

    static void pushFree(int level, int startUnit) {
        next[startUnit] = freeHead[level];
        freeHead[level] = startUnit;
        freeBlockLevel[startUnit] = level;
    }

    static boolean removeFromFreeList(int level, int startUnit) {
        int head = freeHead[level];
        if (head == -1) return false;
        if (head == startUnit) {
            freeHead[level] = next[startUnit];
            next[startUnit] = -1;
            freeBlockLevel[startUnit] = -1;
            return true;
        }
        int prev = head;
        int cur = next[prev];
        while (cur != -1) {
            if (cur == startUnit) {
                next[prev] = next[cur];
                next[cur] = -1;
                freeBlockLevel[cur] = -1;
                return true;
            }
            prev = cur;
            cur = next[cur];
        }
        return false;
    }

    static int allocateBlockByUnits(int unitsRequest) {
        int targetLevel = levelOfUnits(unitsRequest);
        int levelFound = targetLevel;
        while (levelFound <= MAX_LEVEL && freeHead[levelFound] == -1) {
            levelFound = levelFound + 1;
        }
        if (levelFound > MAX_LEVEL) return -1;
        int start = popFreeHead(levelFound);
        while (levelFound > targetLevel) {
            levelFound = levelFound - 1;
            int half = 1 << levelFound;
            int right = start + half;
            pushFree(levelFound, right);
            freeBlockLevel[start] = -1;
        }
        allocBlockLevel[start] = targetLevel;
        freeBlockLevel[start] = -1;
        return start;
    }

    static void freeBlock(int startUnit) {
        int level = allocBlockLevel[startUnit];
        if (level == -1) return;
        allocBlockLevel[startUnit] = -1;
        int s = startUnit;
        int L = level;
        while (L < LEVELS) {
            int sizeUnits = 1 << L;
            int buddy = s ^ sizeUnits;
            if (buddy < 0 || buddy >= UNITS) {
                pushFree(L, s);
                break;
            }
            if (freeBlockLevel[buddy] != L) {
                pushFree(L, s);
                break;
            }
            boolean removed = removeFromFreeList(L, buddy);
            if (!removed) {
                pushFree(L, s);
                break;
            }
            if (buddy < s) s = buddy;
            L = L + 1;
            if (L > MAX_LEVEL) {
                pushFree(MAX_LEVEL, s);
                break;
            }
        }
    }

    static void allocateProgramDecomposed(int pid) {
        int requestedBytes = progRequestedBytes[pid];
        int remainingUnits = (requestedBytes + UNIT_BYTES - 1) / UNIT_BYTES;
        while (remainingUnits > 0) {
            int pow = 1;
            while ((pow << 1) <= remainingUnits) pow = pow << 1;
            int start = allocateBlockByUnits(pow);
            if (start == -1) {
                int tryPow = pow >> 1;
                while (tryPow >= 1) {
                    start = allocateBlockByUnits(tryPow);
                    if (start != -1) break;
                    tryPow = tryPow >> 1;
                }
                if (start == -1) {
                    System.out.println("Falha ao alocar programa " + progLabel[pid]);
                    return;
                } else {
                    int idx = pid * MAX_BLOCKS_PER_PROG + progBlockCount[pid];
                    progBlockStart[idx] = start;
                    progBlockLevel[idx] = levelOfUnits(tryPow);
                    // debug
                    System.out.println("DEBUG: alocado (fallback) prog=" + progLabel[pid] +
                                       " units=" + tryPow + " start=" + start +
                                       " offset=" + startUnitToByteOffset(start));
                    progBlockCount[pid]++;
                    remainingUnits -= tryPow;
                }
            } else {
                int idx = pid * MAX_BLOCKS_PER_PROG + progBlockCount[pid];
                progBlockStart[idx] = start;
                progBlockLevel[idx] = levelOfUnits(pow);
                // debug
                System.out.println("DEBUG: alocado prog=" + progLabel[pid] +
                                   " units=" + pow + " start=" + start +
                                   " offset=" + startUnitToByteOffset(start));
                progBlockCount[pid]++;
                remainingUnits -= pow;
            }
        }
    }

    static void printAllocatedDetailed() {
        System.out.println("\n BLOCOS ALOCADOS");
        int p = 0;
        while (p < progCount) {
            System.out.println("Programa " + progLabel[p] + " | solicitado=" + progRequestedBytes[p] + " bytes");
            int cnt = progBlockCount[p];
            if (cnt == 0) System.out.println("  (nenhum bloco)");
            else {
                int i = 0;
                while (i < cnt) {
                    int idx = p * MAX_BLOCKS_PER_PROG + i;
                    int start = progBlockStart[idx];
                    int lvl = progBlockLevel[idx];
                    int blockBytes = levelToBytes(lvl);
                    int offset = startUnitToByteOffset(start);
                    System.out.println("  Bloco[" + i + "]: tam=" + blockBytes + " bytes | offset=" + offset);
                    i++;
                }
            }
            p++;
        }
    }

    //imprime blocos alocados ordenados por offset
    static void printAllocatedSorted() {
        // construir lista de blocos (start, bytes, label)
        int totalBlocks = 0;
        int p = 0;
        while (p < progCount) {
            totalBlocks += progBlockCount[p];
            p++;
        }
        if (totalBlocks == 0) {
            System.out.println("\n NENHUM BLOCO ALOCADO ");
            return;
        }
        // arrays paralelos
        int[] starts = new int[totalBlocks];
        int[] bytesArr = new int[totalBlocks];
        char[] owner = new char[totalBlocks];
        int idxOut = 0;
        p = 0;
        while (p < progCount) {
            int cnt = progBlockCount[p];
            int i = 0;
            while (i < cnt) {
                int idx = p * MAX_BLOCKS_PER_PROG + i;
                starts[idxOut] = progBlockStart[idx];
                bytesArr[idxOut] = levelToBytes(progBlockLevel[idx]);
                owner[idxOut] = progLabel[p];
                idxOut++;
                i++;
            }
            p++;
        }
        // selection sort por starts
        int n = totalBlocks;
        int s = 0;
        while (s < n) {
            int minj = s;
            int j = s + 1;
            while (j < n) {
                if (starts[j] < starts[minj]) minj = j;
                j++;
            }
            // swap s <-> minj
            if (minj != s) {
                int tmpS = starts[s]; starts[s] = starts[minj]; starts[minj] = tmpS;
                int tmpB = bytesArr[s]; bytesArr[s] = bytesArr[minj]; bytesArr[minj] = tmpB;
                char tmpO = owner[s]; owner[s] = owner[minj]; owner[minj] = tmpO;
            }
            s++;
        }
        // imprimir
        System.out.println("\nBLOCOS ALOCADOS (ordenados por offset)");
        int k = 0;
        while (k < n) {
            int off = startUnitToByteOffset(starts[k]);
            System.out.println("Owner=" + owner[k] + " | offset=" + off + " | bytes=" + bytesArr[k]);
            k++;
        }
    }

    // mapa da memória por segmentos
    static void printMemoryMap() {
        // montar array owner por unidade (0 => livre, else label)
        char[] unitOwner = new char[UNITS];
        int u = 0;
        while (u < UNITS) { unitOwner[u] = 0; u++; }
        // preencher pelos blocos alocados
        int p = 0;
        while (p < progCount) {
            int cnt = progBlockCount[p];
            int i = 0;
            while (i < cnt) {
                int idx = p * MAX_BLOCKS_PER_PROG + i;
                int start = progBlockStart[idx];
                int lvl = progBlockLevel[idx];
                int lenUnits = 1 << lvl;
                int j = 0;
                while (j < lenUnits) {
                    unitOwner[start + j] = progLabel[p];
                    j++;
                }
                i++;
            }
            p++;
        }
        // imprimir segmentos
        System.out.println("\n=== MAPA DA MEMÓRIA (segmentos) ===");
        int s = 0;
        while (s < UNITS) {
            char o = unitOwner[s];
            if (o != 0) {
                // ocupado por arquivo 'o' — contar segmento contíguo
                int t = s;
                while (t < UNITS && unitOwner[t] == o) t++;
                int unitsLen = t - s;
                int offset = startUnitToByteOffset(s);
                int bytesLen = unitsLen * UNIT_BYTES;
                System.out.println("OFFSET=" + offset + " BYTES=" + bytesLen + " OWNER=" + o);
                s = t;
            } else {
                // livre — podemos pular baseado em freeBlockLevel se houver um bloco
                int lvl = freeBlockLevel[s];
                if (lvl != -1) {
                    int unitsLen = 1 << lvl;
                    int offset = startUnitToByteOffset(s);
                    int bytesLen = unitsLen * UNIT_BYTES;
                    System.out.println("OFFSET=" + offset + " BYTES=" + bytesLen + " OWNER=LIVRE (level=" + lvl + ")");
                    s = s + unitsLen;
                } else {
                    // nenhuma marcação — unidade livre isolada (deveria não ocorrer, mas tratamos)
                    int offset = startUnitToByteOffset(s);
                    System.out.println("OFFSET=" + offset + " BYTES=" + UNIT_BYTES + " OWNER=LIVRE (unmarked)");
                    s++;
                }
            }
        }
    }

    static void printFreeBlocks() { // mantém varredura direta por freeBlockLevel (confiável)
        System.out.println("\n=== BLOCOS LIVRES (varredura freeBlockLevel) ===");
        int s = 0;
        while (s < UNITS) {
            int lvl = freeBlockLevel[s];
            if (lvl != -1) {
                int bytes = levelToBytes(lvl);
                int offset = startUnitToByteOffset(s);
                System.out.println("Livre | offset=" + offset + " | bytes=" + bytes + " | level=" + lvl);
                s = s + (1 << lvl);
            } else {
                s++;
            }
        }
    }

    static int totalOccupiedBytes() {
        int sum = 0;
        int p = 0;
        while (p < progCount) {
            int cnt = progBlockCount[p];
            int i = 0;
            while (i < cnt) {
                int idx = p * MAX_BLOCKS_PER_PROG + i;
                int lvl = progBlockLevel[idx];
                int bytes = levelToBytes(lvl);
                sum += bytes;
                i++;
            }
            p++;
        }
        return sum;
    }

    static int totalFreeBytesScan() {
        int sum = 0;
        int s = 0;
        while (s < UNITS) {
            int lvl = freeBlockLevel[s];
            if (lvl != -1) {
                sum += levelToBytes(lvl);
                s += (1 << lvl);
            } else s++;
        }
        return sum;
    }

    static int startUnitToByteOffset(int startUnit) {
        return startUnit * UNIT_BYTES;
    }

    static int levelToBytes(int level) {
        return UNIT_BYTES * (1 << level);
    }

    public static void main(String[] args) {
        init();

        loadFile("A.txt");
        loadFile("B.txt");
        loadFile("C.txt");

        int p = 0;
        while (p < progCount) {
            allocateProgramDecomposed(p);
            p++;
        }

        printAllocatedDetailed();
        printAllocatedSorted();
        printMemoryMap();
        printFreeBlocks();

        int used = totalOccupiedBytes();
        int freeByCalc = TOTAL_BYTES - used;
        int freeScan = totalFreeBytesScan();

        System.out.println("\nAREA TOTAL OCUPADA = " + used + " bytes");
        System.out.println("AREA TOTAL LIVRE (scan freeBlockLevel)  = " + freeScan + " bytes");

        // validação de consistência
        if (freeByCalc != freeScan) {
            System.out.println("\nAVISO: inconsistencia detectada entre calculos (calc vs scan).");
        } else {
            System.out.println("\nVALIDAÇÃO OK: ocupado + livre = TOTAL_BYTES");
        }
    }
}
