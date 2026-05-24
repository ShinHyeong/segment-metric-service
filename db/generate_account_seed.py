import csv
import random
import sys
import os  # 디렉토리 생성을 위해 추가

ROWS = 30_000_000
BATCH_SIZE = 100_000
SEED = 42

random.seed(SEED)

# ============================================================
# location: 행정안전부 주민등록 인구통계 (2026년 4월 기준)
# ============================================================
LOCATION_WEIGHTS = [
    ('경기', 26.91), ('서울', 18.20), ('부산', 6.33), ('경남', 6.26),
    ('인천', 5.98),  ('경북', 4.89),  ('대구', 4.60), ('충남', 4.18),
    ('전남', 3.47),  ('전북', 3.37),  ('충북', 3.13), ('강원', 2.95),
    ('대전', 2.82),  ('광주', 2.71),  ('울산', 2.13), ('제주', 1.30),
    ('세종', 0.77),
]
LOCATIONS = [loc for loc, _ in LOCATION_WEIGHTS]
LOCATION_PROBS = [w for _, w in LOCATION_WEIGHTS]

# ============================================================
# gender: 실제 인구 비율 + OTHER(응답없음) 1%
# ============================================================
GENDERS = ['MALE', 'FEMALE', 'OTHER']
GENDER_PROBS = [49.25, 49.75, 1.0]

# ============================================================
# 연령대 분포 (만 14세 ~ 만 99세, IT 결제 서비스 가중치 반영)
# ============================================================
AGE_GROUPS = [
    ('teens',  4),   # 14-19
    ('20s',    20),  # 20-29
    ('30s',    28),  # 30-39
    ('40s',    25),  # 40-49
    ('50s',    14),  # 50-59
    ('60s',    7),   # 60-69
    ('70plus', 2),   # 70-99
]
AGE_GROUP_NAMES = [n for n, _ in AGE_GROUPS]
AGE_GROUP_PROBS = [w for _, w in AGE_GROUPS]

# 70대 이상 1세 단위 분포 (5세 버킷)
AGE_70PLUS_BUCKETS = [(70, 74), (75, 79), (80, 84), (85, 89), (90, 99)]
AGE_70PLUS_PROBS = [55, 27, 13, 4, 1]


def sample_age(group):
    if group == 'teens':
        return random.randint(14, 19)
    if group == '20s':
        # 20-24: 43%, 25-29: 57%
        if random.random() < 0.43:
            return random.randint(20, 24)
        return random.randint(25, 29)
    if group == '30s':
        return random.randint(30, 39)
    if group == '40s':
        return random.randint(40, 49)
    if group == '50s':
        return random.randint(50, 59)
    if group == '60s':
        return random.randint(60, 69)
    # 70plus
    lo, hi = random.choices(AGE_70PLUS_BUCKETS, weights=AGE_70PLUS_PROBS, k=1)[0]
    return random.randint(lo, hi)


# ============================================================
# order_count 분포 (연령대별)
# 버킷: 0 / 1 / 2-5 / 6-20 / 21-50 / 51-100 / 101-200 / 201-500
# ============================================================
ORDER_BUCKETS = [
    (0, 0), (1, 1), (2, 5), (6, 20),
    (21, 50), (51, 100), (101, 200), (201, 500),
]

ORDER_PROBS_BY_GROUP = {
    'teens':  [70,  8,  17,  5,   0,   0,    0,    0],
    '20s':    [20,  8,  35,  22,  10,  3,    1.5,  0.5],
    '30s':    [7,   5,  22,  26,  22,  12,   5,    1],
    '40s':    [12,  6,  28,  24,  18,  8,    3,    1],
    '50s':    [18,  7,  33,  22,  12,  5,    2.5,  0.5],
    '60s':    [38,  9,  28,  15,  7,   2,    0.8,  0.2],
    '70plus': [70,  9,  14,  5,   2,   0,    0,    0],
}


def sample_order_count(group):
    lo, hi = random.choices(ORDER_BUCKETS, weights=ORDER_PROBS_BY_GROUP[group], k=1)[0]
    if lo == hi:
        return lo
    return random.randint(lo, hi)


# ============================================================
# CSV 생성
# ============================================================
# 맥북 절대 경로 설정 및 폴더 생성
output_dir = '/Users/psh/Documents/segment-metric-service/db'
os.makedirs(output_dir, exist_ok=True)  # 해당 경로에 폴더가 없다면 생성
output_file = os.path.join(output_dir, 'accounts.csv')

with open(output_file, 'w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    n = 0
    while n < ROWS:
        batch = min(BATCH_SIZE, ROWS - n)
        # 배치 단위 샘플링으로 random.choices 호출 오버헤드 절감
        genders = random.choices(GENDERS, weights=GENDER_PROBS, k=batch)
        locations = random.choices(LOCATIONS, weights=LOCATION_PROBS, k=batch)
        groups = random.choices(AGE_GROUP_NAMES, weights=AGE_GROUP_PROBS, k=batch)

        rows = []
        for i in range(batch):
            n += 1
            g = groups[i]
            rows.append([
                f'user_{n}',
                genders[i],
                sample_age(g),
                locations[i],
                sample_order_count(g),
            ])
        writer.writerows(rows)

        if n % 1_000_000 == 0:
            print(f'{n:,} rows generated', file=sys.stderr)
