#include <Servo.h>
#include <stdlib.h>
#include <string.h>

// Robot seguidor de linea negra + radar decorativo (Mega 2560)
// QTR: LOW = negro, HIGH = blanco. Motores conservan sentido V2.3.
const byte sensorIzq=2, sensorCentro=3, sensorDer=4;
const byte ENA=5, IN1=6, IN2=7, ENB=10, IN3=8, IN4=9;
const byte PIN_SERVO=11, PIN_TRIG=12, PIN_ECHO=13;

float Kp=75.0, Ki=0.0, Kd=25.0;
int velocidadBase=100, error=0, ultimoError=0, ultimaDireccion=0;
float P=0, I=0, D=0, correccion=0;
const int REFUERZO_CURVA=55, MAX_RETROCESO_CURVA=180, MAX_AVANCE_CURVA=220;
const int velocidadBusqueda=165;
const unsigned long timeoutBusqueda=4500;
bool lineaPerdida=false;
unsigned long inicioPerdida=0, ultimoPID=0;
const unsigned long INTERVALO_PID=15;

Servo servoRadar;
int anguloRadar=90, distanciaRadar=-1;
bool radarActivo=true, robotActivo=true;
const int ANGULO_MIN=20, ANGULO_MAX=160;
int direccionRadar=1;
unsigned long ultimoPasoRadar=0, ultimoEco=0, ultimoCambioAngulo=0;
const unsigned long INTERVALO_PASO_RADAR=60, INTERVALO_ECO=65;
const unsigned long TIMEOUT_ECO=12000;
const unsigned long ESTABILIZACION_SERVO=35;

// Estados compatibles con el dashboard: 0=seguimiento, 16=busqueda, 17=parado.
int estadoActual=0;
char bufferComando[32];
byte indiceComando=0;
unsigned long ultimaTelemetria=0;
const unsigned long INTERVALO_TELEMETRIA=150;

void moverMotores(int velocidadIzq, int velocidadDer) {
  velocidadIzq=constrain(velocidadIzq,-255,255);
  velocidadDer=constrain(velocidadDer,-255,255);
  if (velocidadIzq>=0) {digitalWrite(IN1,LOW);digitalWrite(IN2,HIGH);analogWrite(ENA,velocidadIzq);}
  else {digitalWrite(IN1,HIGH);digitalWrite(IN2,LOW);analogWrite(ENA,-velocidadIzq);}
  if (velocidadDer>=0) {digitalWrite(IN3,LOW);digitalWrite(IN4,HIGH);analogWrite(ENB,velocidadDer);}
  else {digitalWrite(IN3,HIGH);digitalWrite(IN4,LOW);analogWrite(ENB,-velocidadDer);}
}

void reiniciarPID() {
  error=0; ultimoError=0; ultimaDireccion=0;
  P=0; I=0; D=0; correccion=0;
  lineaPerdida=false; inicioPerdida=0;
}

void buscarLinea() {
  estadoActual=16;
  if (millis()-inicioPerdida>=timeoutBusqueda) {moverMotores(0,0);return;}
  if (ultimaDireccion<0) moverMotores(-velocidadBusqueda,velocidadBusqueda);
  else moverMotores(velocidadBusqueda,-velocidadBusqueda);
}

void seguirLinea() {
  if (millis()-ultimoPID<INTERVALO_PID) return;
  ultimoPID=millis();
  int izq=digitalRead(sensorIzq), centro=digitalRead(sensorCentro), der=digitalRead(sensorDer);
  if (izq==HIGH && centro==HIGH && der==HIGH) {
    if (!lineaPerdida) {lineaPerdida=true;inicioPerdida=millis();}
    buscarLinea();
    return;
  }
  if (lineaPerdida) {lineaPerdida=false;ultimoError=0;I=0;}
  estadoActual=0;
  if (izq==HIGH && centro==LOW && der==HIGH) error=0;
  else if (izq==LOW && centro==LOW && der==HIGH) error=-1;
  else if (izq==LOW && centro==HIGH && der==HIGH) error=-2;
  else if (izq==HIGH && centro==LOW && der==LOW) error=1;
  else if (izq==HIGH && centro==HIGH && der==LOW) error=2;
  // Patrones ambiguos conservan ultimo error, igual que V2.3.
  if (error<0) ultimaDireccion=-1;
  else if (error>0) ultimaDireccion=1;
  P=error;
  I=constrain(I+error,-100.0f,100.0f);
  D=error-ultimoError;
  correccion=Kp*P+Ki*I+Kd*D;
  ultimoError=error;
  if (error==-2) correccion-=REFUERZO_CURVA;
  else if (error==2) correccion+=REFUERZO_CURVA;
  int velocidadIzq=constrain((int)(velocidadBase+correccion),-MAX_RETROCESO_CURVA,MAX_AVANCE_CURVA);
  int velocidadDer=constrain((int)(velocidadBase-correccion),-MAX_RETROCESO_CURVA,MAX_AVANCE_CURVA);
  moverMotores(velocidadIzq,velocidadDer);
}

int medirDistancia() {
  digitalWrite(PIN_TRIG,LOW);delayMicroseconds(2);
  digitalWrite(PIN_TRIG,HIGH);delayMicroseconds(10);
  digitalWrite(PIN_TRIG,LOW);
  unsigned long duracion=pulseIn(PIN_ECHO,HIGH,TIMEOUT_ECO);
  if (duracion==0) return -1;
  int cm=(int)(duracion*0.0343f/2.0f);
  return (cm>=2 && cm<=400) ? cm : -1;
}

void actualizarRadar() {
  if (!radarActivo) return;
  unsigned long ahora=millis();
  if (ahora-ultimoPasoRadar>=INTERVALO_PASO_RADAR) {
    ultimoPasoRadar=ahora;
    anguloRadar+=direccionRadar;
    if (anguloRadar>=ANGULO_MAX) {anguloRadar=ANGULO_MAX;direccionRadar=-1;}
    if (anguloRadar<=ANGULO_MIN) {anguloRadar=ANGULO_MIN;direccionRadar=1;}
    servoRadar.write(anguloRadar);
    ultimoCambioAngulo=ahora;
  }
  if (ahora-ultimoEco>=INTERVALO_ECO && ahora-ultimoCambioAngulo>=ESTABILIZACION_SERVO) {
    ultimoEco=ahora;
    distanciaRadar=medirDistancia();
  }
}

void procesarComando(char *comando) {
  for (byte i=0;comando[i];i++) if (comando[i]>='a' && comando[i]<='z') comando[i]-=32;
  if (!strcmp(comando,"START")) {reiniciarPID();robotActivo=true;estadoActual=0;Serial1.println("OK: START");return;}
  if (!strcmp(comando,"STOP")) {robotActivo=false;estadoActual=17;moverMotores(0,0);Serial1.println("OK: STOP");return;}
  if (!strcmp(comando,"STATUS")) {
    Serial1.print("ACTIVO=");Serial1.println(robotActivo?1:0);
    Serial1.print("ESTADO=");Serial1.println(estadoActual);
    Serial1.print("KP=");Serial1.println(Kp,2);
    Serial1.print("KI=");Serial1.println(Ki,2);
    Serial1.print("KD=");Serial1.println(Kd,2);
    Serial1.print("VEL=");Serial1.println(velocidadBase);
    Serial1.print("RADAR=");Serial1.println(radarActivo?"ON":"OFF");
    Serial1.println("EVASION=OFF (DESHABILITADA)");return;
  }
  if (!strcmp(comando,"DIST")) {Serial1.print("ANGULO=");Serial1.println(anguloRadar);Serial1.print("DISTANCIA=");Serial1.println(distanciaRadar);return;}
  if (!strncmp(comando,"KP=",3)) {Kp=atof(comando+3);reiniciarPID();Serial1.println("OK: KP");return;}
  if (!strncmp(comando,"KI=",3)) {Ki=atof(comando+3);reiniciarPID();Serial1.println("OK: KI");return;}
  if (!strncmp(comando,"KD=",3)) {Kd=atof(comando+3);reiniciarPID();Serial1.println("OK: KD");return;}
  if (!strncmp(comando,"VEL=",4)) {velocidadBase=constrain(atoi(comando+4),0,255);Serial1.println("OK: VEL");return;}
  if (!strcmp(comando,"RADAR ON")) {radarActivo=true;ultimoPasoRadar=millis();Serial1.println("OK: RADAR ON");return;}
  if (!strcmp(comando,"RADAR OFF")) {radarActivo=false;distanciaRadar=-1;Serial1.println("OK: RADAR OFF");return;}
  if (!strcmp(comando,"EVASION ON") || !strcmp(comando,"EVASION OFF")) {Serial1.println("OK: EVASION DESHABILITADA");return;}
  Serial1.println("ERROR: COMANDO");
}

void leerBluetooth() {
  byte procesados=0;
  while (Serial1.available() && procesados<32) {
    char c=Serial1.read();procesados++;
    if (c=='\n' || c=='\r') {
      if (indiceComando) {bufferComando[indiceComando]='\0';procesarComando(bufferComando);indiceComando=0;}
    } else if (indiceComando<sizeof(bufferComando)-1) bufferComando[indiceComando++]=c;
    else indiceComando=0;
  }
}

void enviarTelemetria() {
  if (millis()-ultimaTelemetria<INTERVALO_TELEMETRIA) return;
  ultimaTelemetria=millis();
  Serial1.print('$');Serial1.print(robotActivo?1:0);Serial1.print(',');
  Serial1.print(error);Serial1.print(',');Serial1.print(anguloRadar);Serial1.print(',');
  Serial1.print(distanciaRadar);Serial1.print(',');Serial1.println(estadoActual);
}

void setup() {
  Serial.begin(115200);Serial1.begin(9600);
  pinMode(sensorIzq,INPUT);pinMode(sensorCentro,INPUT);pinMode(sensorDer,INPUT);
  pinMode(ENA,OUTPUT);pinMode(IN1,OUTPUT);pinMode(IN2,OUTPUT);
  pinMode(ENB,OUTPUT);pinMode(IN3,OUTPUT);pinMode(IN4,OUTPUT);
  pinMode(PIN_TRIG,OUTPUT);pinMode(PIN_ECHO,INPUT);
  digitalWrite(PIN_TRIG,LOW);moverMotores(0,0);
  servoRadar.attach(PIN_SERVO);servoRadar.write(anguloRadar);
  ultimoPasoRadar=millis();ultimoCambioAngulo=millis();
  Serial.println("ROBOT PID + RADAR V3.0 (SIN EVASION)");
  Serial1.println("ROBOT PID + RADAR V3.0 (SIN EVASION)");
}

void loop() {
  leerBluetooth();
  actualizarRadar();
  if (robotActivo) seguirLinea();
  else {moverMotores(0,0);estadoActual=17;}
  enviarTelemetria();
}
