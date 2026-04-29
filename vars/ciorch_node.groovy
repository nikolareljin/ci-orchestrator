def call(Map args = [:]) {
    ciorch([adapter: 'node'] + args)
}
