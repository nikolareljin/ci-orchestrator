def call(Map args = [:]) {
    ciorch([adapter: 'php'] + args)
}
