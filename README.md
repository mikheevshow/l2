# Leaderless Replication — Java

Реализация leaderless-репликации на Java (Spring Boot) с Docker-контейнерами.

## Структура

```
.
├── replica/        # Spring Boot HTTP-сервис — реплика и координатор
├── client/         # Java-клиент с непрерывным циклом записи/чтения
├── docker-compose.yml              # Сценарий 1: W+R > N (консистентный)
├── docker-compose.scenario2.yml   # Сценарий 2: W+R ≤ N (неконсистентный)
├── docker-compose.scenario3.yml   # Сценарий 3: одна реплика упала — кворум есть
└── docker-compose.scenario4.yml   # Сценарий 4: две реплики упали — кворум недостижим
```

## API

### Внешний (для клиента)

| Метод | Путь     | Описание                                      |
|-------|----------|-----------------------------------------------|
| POST  | /value   | Скоординировать запись на кворум              |
| GET   | /value   | Скоординировать чтение с кворума              |

### Внутренний (межрепликационный)

| Метод | Путь              | Описание                                               |
|-------|-------------------|--------------------------------------------------------|
| GET   | /internal/value   | Вернуть локальное значение и версию                    |
| POST  | /internal/value   | Применить запись с задержкой (если версия ≥ текущей)   |

## Переменные окружения реплики

| Переменная                | По умолчанию | Описание                                          |
|---------------------------|-------------|---------------------------------------------------|
| `N`                       | 3           | Общее число реплик                                |
| `W`                       | 2           | Кворум записи                                     |
| `R`                       | 2           | Кворум чтения                                     |
| `REPLICAS`                | —           | Запятая-разделённый список URL всех реплик        |
| `REPLICATION_DELAY_MAX_MS`| 1000        | Макс. задержка репликации, мс                     |
| `SERVER_PORT`             | 8080        | Порт сервиса                                      |

## Запуск сценариев

### Сценарий 1 — консистентный (N=3, W=2, R=2)

```bash
docker compose up --build
```

Ожидаемый вывод клиента:
```
ok: 142 | inconsistent: 0 | errors: 0
```

### Сценарий 2 — неконсистентный (N=3, W=1, R=1)

```bash
docker compose -f docker-compose.scenario2.yml up --build
```

Ожидаемый вывод клиента:
```
ok: 50 | inconsistent: 30 | errors: 0
```

### Сценарий 3 — одна реплика упала, кворум сохранён

```bash
docker compose -f docker-compose.scenario3.yml up --build -d
docker compose -f docker-compose.scenario3.yml stop replica3
docker compose -f docker-compose.scenario3.yml logs -f client
```

Ожидаемый вывод:
```
ok: 87 | inconsistent: 0 | errors: 0
```

### Сценарий 4 — две реплики упали, кворум недостижим

```bash
docker compose -f docker-compose.scenario4.yml up --build -d
docker compose -f docker-compose.scenario4.yml stop replica2 replica3
docker compose -f docker-compose.scenario4.yml logs -f client
```

Ожидаемый вывод:
```
ok: 0 | inconsistent: 0 | errors: 73
```
